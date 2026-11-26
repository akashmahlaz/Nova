//! Settling a ride's money the way Uber does (see migrations/0019_trip_pricing.sql).
//!
//! The rider is shown an upfront price. At drop-off it stands unless the trip changed
//! significantly, in which case the fare is recomputed on the actual time and distance at the
//! same rate card and surge. Wait time after the grace period is added on top. The driver earns
//! the fare minus the booking fee minus the area's service fee, plus long pickup pay (which the
//! platform pays, not the rider). Everything here is pure so it can be tested without a database.

use sqlx::{FromRow, PgExecutor};
use uuid::Uuid;

use crate::error::ApiError;

/// A trip at least this much longer than its route (on top of the area's percentage) counts as
/// changed. Stops tiny trips from repricing over a few hundred metres of GPS noise.
const MIN_EXTRA_METERS: i64 = 2_000;
/// Same idea for time: a few minutes of traffic on a short trip doesn't reprice it.
const MIN_EXTRA_SECONDS: i64 = 10 * 60;

/// The rate card a ride was booked on (`ride_rate_cards`), plus the area's market rules.
#[derive(Debug, Clone, FromRow)]
pub struct Card {
    pub base_cents: i32,
    pub per_km_cents: i32,
    pub per_min_cents: i32,
    pub minimum_cents: i32,
    pub booking_fee_cents: i32,
    pub wait_per_min_cents: i32,
    pub wait_grace_secs: i32,
    pub cancel_fee_cents: i32,
    pub cancel_grace_secs: i32,
    pub no_show_wait_secs: i32,
    pub long_pickup_after_secs: i32,
    pub rounding_minor: i32,
    pub service_fee_bps: i32,
    pub cancel_fee_max_bps: i32,
    pub reprice_over_pct: i32,
    pub dropoff_radius_m: i32,
    pub arrive_radius_m: i32,
}

/// Copies the current rate card and area rules onto a ride. Runs at booking; for rides booked
/// before cards were copied it runs on first use. Never overwrites an existing copy.
const SNAPSHOT_SQL: &str = "INSERT INTO ride_rate_cards (
        ride_id, base_cents, per_km_cents, per_min_cents, minimum_cents, booking_fee_cents,
        wait_per_min_cents, wait_grace_secs, cancel_fee_cents, cancel_grace_secs, no_show_wait_secs,
        long_pickup_after_secs, rounding_minor, service_fee_bps, cancel_fee_max_bps,
        reprice_over_pct, dropoff_radius_m, arrive_radius_m)
     SELECT r.id, f.base_cents, f.per_km_cents, f.per_min_cents, f.minimum_cents, f.booking_fee_cents,
        f.wait_per_min_cents, f.wait_grace_secs, f.cancel_fee_cents, f.cancel_grace_secs, f.no_show_wait_secs,
        f.long_pickup_after_secs, a.fare_rounding_minor, a.service_fee_bps, a.cancel_fee_max_bps,
        a.reprice_over_pct, a.dropoff_radius_m, a.arrive_radius_m
     FROM rides r
     JOIN fare_rates f ON f.service_area_id = r.service_area_id AND f.product_id = r.product_id
     JOIN service_areas a ON a.id = r.service_area_id
     WHERE r.id = $1
     ON CONFLICT (ride_id) DO NOTHING";

pub async fn snapshot<'e>(db: impl PgExecutor<'e>, ride: Uuid) -> Result<(), ApiError> {
    sqlx::query(SNAPSHOT_SQL).bind(ride).execute(db).await?;
    Ok(())
}

/// The ride's rate card, copying it first if the ride predates copies.
pub async fn card(pool: &sqlx::PgPool, ride: Uuid) -> Result<Card, ApiError> {
    snapshot(pool, ride).await?;
    sqlx::query_as::<_, Card>(
        "SELECT base_cents, per_km_cents, per_min_cents, minimum_cents, booking_fee_cents,
                wait_per_min_cents, wait_grace_secs, cancel_fee_cents, cancel_grace_secs, no_show_wait_secs,
                long_pickup_after_secs, rounding_minor, service_fee_bps, cancel_fee_max_bps,
                reprice_over_pct, dropoff_radius_m, arrive_radius_m
         FROM ride_rate_cards WHERE ride_id = $1",
    )
    .bind(ride)
    .fetch_optional(pool)
    .await?
    .ok_or(ApiError::Conflict("no_rate_card"))
}

/// What happened on the trip, measured by the server.
#[derive(Debug, Clone, Copy)]
pub struct Trip {
    /// The price the rider accepted.
    pub upfront_cents: i64,
    pub surge: f64,
    /// The route the upfront price was based on.
    pub route_meters: i64,
    pub route_seconds: i64,
    /// Driven on trip: the GPS trail's length, never less than the straight line.
    pub actual_meters: i64,
    pub actual_seconds: i64,
    /// From where the trip ended to the destination.
    pub dropoff_distance_m: i64,
    /// From Arrived to Start.
    pub wait_seconds: i64,
    /// From Accept to Arrived, and the distance driven in that time.
    pub pickup_seconds: i64,
    pub pickup_meters: i64,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Settlement {
    pub fare_cents: i64,
    /// "upfront" or "actual".
    pub basis: &'static str,
    /// Why the fare moved off the upfront price.
    pub reason: Option<&'static str>,
    pub wait_fee_cents: i64,
    pub long_pickup_cents: i64,
    pub service_fee_cents: i64,
    pub driver_earnings_cents: i64,
}

fn time_distance(card: &Card, meters: i64, seconds: i64) -> f64 {
    card.base_cents as f64
        + card.per_km_cents as f64 * meters.max(0) as f64 / 1000.0
        + card.per_min_cents as f64 * seconds.max(0) as f64 / 60.0
}

fn round_to(step: i32, value: f64) -> i64 {
    let step = step.max(1) as f64;
    ((value / step).round() * step) as i64
}

fn service_fee(card: &Card, fare: i64) -> i64 {
    ((fare - card.booking_fee_cents as i64).max(0) as f64 * card.service_fee_bps as f64 / 10_000.0).round() as i64
}

/// Did the trip change enough to leave the upfront price? Returns the reason.
pub fn change_reason(card: &Card, t: &Trip) -> Option<&'static str> {
    if t.dropoff_distance_m > card.dropoff_radius_m as i64 {
        return Some("ended_away");
    }
    let over = 1.0 + card.reprice_over_pct as f64 / 100.0;
    let further = t.actual_meters as f64 > t.route_meters as f64 * over && t.actual_meters - t.route_meters >= MIN_EXTRA_METERS;
    let slower = t.actual_seconds as f64 > t.route_seconds as f64 * over && t.actual_seconds - t.route_seconds >= MIN_EXTRA_SECONDS;
    (further || slower).then_some("longer_trip")
}

pub fn settle(card: &Card, t: &Trip) -> Settlement {
    let surge = t.surge.max(1.0);
    let reason = change_reason(card, t);
    let trip = match reason {
        Some(_) => (time_distance(card, t.actual_meters, t.actual_seconds) * surge + card.booking_fee_cents as f64)
            .max(card.minimum_cents as f64),
        None => t.upfront_cents as f64,
    };
    let billable_wait = (t.wait_seconds - card.wait_grace_secs as i64).max(0);
    let wait_fee = card.wait_per_min_cents as f64 * billable_wait as f64 / 60.0 * surge;
    let fare = round_to(card.rounding_minor, trip + wait_fee);
    let wait_fee_cents = if billable_wait > 0 { round_to(1, wait_fee) } else { 0 };

    let long_pickup_cents = {
        let extra = t.pickup_seconds - card.long_pickup_after_secs as i64;
        if extra > 0 && t.pickup_seconds > 0 {
            let extra_m = t.pickup_meters as f64 * extra as f64 / t.pickup_seconds as f64;
            round_to(1, card.per_min_cents as f64 * extra as f64 / 60.0 + card.per_km_cents as f64 * extra_m / 1000.0)
        } else {
            0
        }
    };
    let service_fee_cents = service_fee(card, fare);
    let driver_earnings_cents = (fare - card.booking_fee_cents as i64).max(0) - service_fee_cents + long_pickup_cents;
    Settlement {
        fare_cents: fare,
        basis: if reason.is_some() { "actual" } else { "upfront" },
        reason,
        wait_fee_cents,
        long_pickup_cents,
        service_fee_cents,
        driver_earnings_cents,
    }
}

/// Cancellation or no-show fee: the card's fee, capped at the area's share of the upfront fare
/// (India caps it at 10%), rounded like fares. Returns (rider fee, service fee, driver earnings).
pub fn cancellation(card: &Card, upfront_cents: i64) -> (i64, i64, i64) {
    let cap = upfront_cents as f64 * card.cancel_fee_max_bps as f64 / 10_000.0;
    let fee = round_to(card.rounding_minor, (card.cancel_fee_cents as f64).min(cap));
    let service = (fee as f64 * card.service_fee_bps as f64 / 10_000.0).round() as i64;
    (fee, service, fee - service)
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The Punjab EV card in paise: ₹30 base, ₹11/km, ₹1.50/min, ₹60 minimum, ₹10 booking fee.
    fn card() -> Card {
        Card {
            base_cents: 3000,
            per_km_cents: 1100,
            per_min_cents: 150,
            minimum_cents: 6000,
            booking_fee_cents: 1000,
            wait_per_min_cents: 150,
            wait_grace_secs: 120,
            cancel_fee_cents: 6000,
            cancel_grace_secs: 180,
            no_show_wait_secs: 300,
            long_pickup_after_secs: 600,
            rounding_minor: 100,
            service_fee_bps: 2000,
            cancel_fee_max_bps: 1000,
            reprice_over_pct: 25,
            dropoff_radius_m: 500,
            arrive_radius_m: 320,
        }
    }

    /// Qadian -> Amritsar as booked: 60.9 km, 81 min, ₹831 upfront.
    fn trip() -> Trip {
        Trip {
            upfront_cents: 83_100,
            surge: 1.0,
            route_meters: 60_937,
            route_seconds: 4_852,
            actual_meters: 61_500,
            actual_seconds: 4_700,
            dropoff_distance_m: 40,
            wait_seconds: 60,
            pickup_seconds: 300,
            pickup_meters: 2_000,
        }
    }

    #[test]
    fn upfront_price_holds_on_a_normal_trip() {
        let s = settle(&card(), &trip());
        assert_eq!(s.fare_cents, 83_100);
        assert_eq!(s.basis, "upfront");
        assert_eq!(s.wait_fee_cents, 0);
        // (831 - 10) × 80% = 656.80 to the driver.
        assert_eq!(s.service_fee_cents, 16_420);
        assert_eq!(s.driver_earnings_cents, 65_680);
    }

    #[test]
    fn ending_early_far_from_the_destination_charges_what_was_driven() {
        // The test ride: ended 3 minutes in, still in Qadian.
        let t = Trip { actual_meters: 800, actual_seconds: 180, dropoff_distance_m: 58_000, ..trip() };
        let s = settle(&card(), &t);
        assert_eq!(s.reason, Some("ended_away"));
        // 30 + 8.80 + 4.50 + 10 = 53.30, below the ₹60 minimum.
        assert_eq!(s.fare_cents, 6_000);
    }

    #[test]
    fn a_much_longer_trip_is_repriced_on_actuals() {
        let t = Trip { actual_meters: 80_000, actual_seconds: 6_600, ..trip() };
        let s = settle(&card(), &t);
        assert_eq!(s.reason, Some("longer_trip"));
        // 30 + 880 + 165 + 10 = 1085.
        assert_eq!(s.fare_cents, 108_500);
    }

    #[test]
    fn a_little_extra_does_not_reprice() {
        let t = Trip { route_meters: 3_000, route_seconds: 600, actual_meters: 4_200, actual_seconds: 900, upfront_cents: 10_000, ..trip() };
        assert_eq!(change_reason(&card(), &t), None);
    }

    #[test]
    fn wait_after_grace_is_charged_with_surge() {
        let t = Trip { wait_seconds: 120 + 4 * 60, surge: 1.5, ..trip() };
        let s = settle(&card(), &t);
        // 4 min × ₹1.50 × 1.5 = ₹9.
        assert_eq!(s.wait_fee_cents, 900);
        assert_eq!(s.fare_cents, 84_000);
    }

    #[test]
    fn long_pickup_is_paid_to_the_driver_not_the_rider() {
        let t = Trip { pickup_seconds: 900, pickup_meters: 9_000, ..trip() };
        let s = settle(&card(), &t);
        assert_eq!(s.fare_cents, 83_100);
        // 5 extra min (₹7.50) and a third of 9 km (₹33) = ₹40.50.
        assert_eq!(s.long_pickup_cents, 4_050);
        assert_eq!(s.driver_earnings_cents, 65_680 + 4_050);
    }

    #[test]
    fn cancellation_fee_is_capped_by_the_area_share() {
        // ₹60 card fee, but 10% of a ₹150 fare is ₹15.
        assert_eq!(cancellation(&card(), 15_000).0, 1_500);
        // 10% of ₹831 is ₹83.10, so the ₹60 fee stands.
        let (fee, service, driver) = cancellation(&card(), 83_100);
        assert_eq!((fee, service, driver), (6_000, 1_200, 4_800));
    }
}
