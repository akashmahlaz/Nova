//! Upfront pricing and H3-based surge.
//!
//! Upfront pricing (the Uber/Lyft model): the rider sees a fixed price before booking. We compute it
//! once from the route distance/duration, lock it in a short-lived quote, and the ride request later
//! references the quote id instead of trusting a price sent by the app.
//!
//! Surge is measured per H3 hexagon (Uber's H3 grid): demand and supply are bucketed into cells so a
//! busy street in one suburb doesn't raise prices across the whole city. Hexagons are used because
//! every neighbour is the same distance away, so riders moving through a city cross cell edges evenly.

use h3o::{CellIndex, LatLng, Resolution};
use redis::AsyncCommands;
use serde::Serialize;
use sqlx::FromRow;

use crate::error::ApiError;

/// Res 8 cells are ~0.7 km² (edge ~460 m): roughly a few city blocks. This is the granularity
/// commonly used for marketplace supply/demand.
pub const SURGE_RESOLUTION: Resolution = Resolution::Eight;

/// Surge can never multiply a fare by more than this. A cap protects riders and the brand.
const MAX_SURGE: f64 = 3.0;

/// Demand counters live this long; a future pricing job reads the recent buckets.
const DEMAND_TTL_SECS: i64 = 15 * 60;

#[derive(Debug, Clone, FromRow)]
pub struct FareRate {
    pub product_id: String,
    pub name: String,
    pub seats: i32,
    pub description: String,
    pub base_cents: i32,
    pub per_km_cents: i32,
    pub per_min_cents: i32,
    pub minimum_cents: i32,
    pub booking_fee_cents: i32,
    /// Round fares to this many minor units (service_areas.fare_rounding_minor): 10 in NZ, 100
    /// for whole rupees, 1 for none. Market data, so no currency is special-cased in code.
    pub rounding_minor: i32,
}

#[derive(Debug, Clone, Serialize, serde::Deserialize)]
pub struct FareOption {
    pub product_id: String,
    pub name: String,
    pub seats: i32,
    pub description: String,
    /// Total upfront price in the area's currency minor units.
    pub fare_cents: i64,
}

/// Fare = (base + per_km × km + per_min × min) × surge + booking fee, at least `minimum`,
/// rounded to the area's rounding step so prices read cleanly ("$24.60", not "$24.63"; "₹245").
/// All amounts are in the currency's minor unit, so the maths is the same for every currency.
/// The booking fee is not surged.
pub fn fare_cents(rate: &FareRate, distance_meters: i64, duration_seconds: i64, surge: f64) -> i64 {
    let km = distance_meters.max(0) as f64 / 1000.0;
    let minutes = duration_seconds.max(0) as f64 / 60.0;
    let trip = rate.base_cents as f64 + rate.per_km_cents as f64 * km + rate.per_min_cents as f64 * minutes;
    let total = trip * surge.clamp(1.0, MAX_SURGE) + rate.booking_fee_cents as f64;
    let total = total.max(rate.minimum_cents as f64);
    let step = rate.rounding_minor.max(1) as f64;
    ((total / step).round() * step) as i64
}

/// The H3 cell a point falls in, or `None` for invalid coordinates (NaN, out of range).
pub fn cell_for(lat: f64, lng: f64) -> Option<CellIndex> {
    LatLng::new(lat, lng).ok().map(|ll| ll.to_cell(SURGE_RESOLUTION))
}

/// Current surge multiplier for a pickup cell.
///
/// The multiplier is written to `nova:surge:{cell}` by a pricing job that compares demand
/// (`nova:demand:*`) against online drivers per cell. Until drivers come online (phase 3) the
/// key is absent and the multiplier is 1.0, so riders see base prices.
pub async fn surge_multiplier(redis: &mut redis::aio::ConnectionManager, cell: CellIndex) -> Result<f64, ApiError> {
    let raw: Option<String> = redis.get(format!("nova:surge:{cell}")).await?;
    Ok(raw
        .and_then(|v| v.parse::<f64>().ok())
        .filter(|v| v.is_finite())
        .map(|v| v.clamp(1.0, MAX_SURGE))
        .unwrap_or(1.0))
}

/// Count one quote request in the pickup cell's current minute bucket. These counters are the
/// demand side of surge; they expire on their own so Redis never grows unbounded.
pub async fn record_demand(redis: &mut redis::aio::ConnectionManager, cell: CellIndex) -> Result<(), ApiError> {
    let minute = chrono::Utc::now().timestamp() / 60;
    let key = format!("nova:demand:{cell}:{minute}");
    let _: () = redis::pipe()
        .incr(&key, 1)
        .expire(&key, DEMAND_TTL_SECS)
        .query_async(redis)
        .await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rate() -> FareRate {
        FareRate {
            product_id: "go".into(),
            name: "Go".into(),
            seats: 4,
            description: String::new(),
            base_cents: 270,
            per_km_cents: 180,
            per_min_cents: 42,
            minimum_cents: 950,
            booking_fee_cents: 150,
            rounding_minor: 10,
        }
    }

    #[test]
    fn prices_distance_and_time() {
        // 9.8 km, 18 min: 270 + 1764 + 756 = 2790, + 150 fee = 2940.
        assert_eq!(fare_cents(&rate(), 9_800, 18 * 60, 1.0), 2940);
    }

    #[test]
    fn short_trips_pay_the_minimum() {
        assert_eq!(fare_cents(&rate(), 300, 60, 1.0), 950);
    }

    #[test]
    fn surge_applies_to_trip_not_fee_and_is_capped() {
        // trip 2790 × 1.5 = 4185 + 150 = 4335 -> rounds to 4340.
        assert_eq!(fare_cents(&rate(), 9_800, 18 * 60, 1.5), 4340);
        // A runaway multiplier is capped at 3x.
        assert_eq!(fare_cents(&rate(), 9_800, 18 * 60, 10.0), fare_cents(&rate(), 9_800, 18 * 60, 3.0));
    }

    #[test]
    fn invalid_coordinates_have_no_cell() {
        assert!(cell_for(f64::NAN, 174.7).is_none());
        assert!(cell_for(-36.85, 174.76).is_some());
    }

    #[test]
    fn rounds_to_the_area_step() {
        // Same trip in an area that rounds to whole units of 100 minor units (e.g. whole rupees).
        let mut r = rate();
        r.rounding_minor = 100;
        assert_eq!(fare_cents(&r, 9_800, 18 * 60, 1.0), 2900);
        r.rounding_minor = 1;
        assert_eq!(fare_cents(&r, 9_800, 18 * 60, 1.0), 2940);
    }
}
