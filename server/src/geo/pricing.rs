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

use crate::{error::ApiError, AppState};

/// Res 8 cells are ~0.7 km² (edge ~460 m): roughly a few city blocks. This is the granularity
/// commonly used for marketplace supply/demand.
pub const SURGE_RESOLUTION: Resolution = Resolution::Eight;

/// Surge can never multiply a fare by more than this. A cap protects riders and the brand.
const MAX_SURGE: f64 = 3.0;

/// Demand counters live this long; the surge job reads the recent buckets.
const DEMAND_TTL_SECS: i64 = 15 * 60;

/// Surge looks at requests in the last few minutes against drivers free right now.
const SURGE_WINDOW_MINUTES: i64 = 5;
/// How often surge is recomputed (Uber recomputes every minute or so).
const SURGE_EVERY_SECS: u64 = 60;
/// Surge curve: multiplier = 1 + SURGE_SLOPE * ln(requests / free drivers). Twice the demand
/// gives about 1.35x, four times about 1.7x. Logarithmic, so it rises fast at first and then
/// flattens instead of running away.
const SURGE_SLOPE: f64 = 0.5;
/// When demand eases, surge falls by this much per minute rather than dropping at once, so a
/// rider who just saw 1.8x doesn't see 1.0x a second later and feel tricked.
const SURGE_STEP_DOWN: f64 = 0.1;
/// Supply is counted within this radius of the cell centre: the cell plus its ring of neighbours.
const SUPPLY_RADIUS_KM: f64 = 1.2;
/// A surge value expires on its own if the job stops.
const SURGE_TTL_SECS: u64 = 3 * 60;

fn demand_cells_key(minute: i64) -> String {
    format!("nova:demand:cells:{minute}")
}

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
    let cells = demand_cells_key(minute);
    let _: () = redis::pipe()
        .incr(&key, 1)
        .expire(&key, DEMAND_TTL_SECS)
        .sadd(&cells, cell.to_string())
        .expire(&cells, DEMAND_TTL_SECS)
        .query_async(redis)
        .await?;
    Ok(())
}

/// Target multiplier for `requests` against `free_drivers`, before the area cap.
pub fn surge_target(requests: i64, free_drivers: i64) -> f64 {
    let ratio = requests as f64 / free_drivers.max(1) as f64;
    if ratio <= 1.0 {
        return 1.0;
    }
    ((1.0 + SURGE_SLOPE * ratio.ln()) * 10.0).round() / 10.0
}

/// Next multiplier: rise straight to the target, ease down by [SURGE_STEP_DOWN] a minute.
pub fn surge_next(previous: f64, target: f64, cap: f64) -> f64 {
    let next = if target >= previous { target } else { (previous - SURGE_STEP_DOWN).max(target) };
    (next.clamp(1.0, cap.clamp(1.0, MAX_SURGE)) * 10.0).round() / 10.0
}

/// Every minute, for each hexagon that had requests recently: count requests in it and its
/// neighbours over the window, count free online drivers around it, and write the multiplier
/// that quotes read. Capped by the service area's `max_surge` (2x in India by law).
pub fn spawn_surge_job(st: AppState) {
    tokio::spawn(async move {
        let mut tick = tokio::time::interval(std::time::Duration::from_secs(SURGE_EVERY_SECS));
        loop {
            tick.tick().await;
            if let Err(err) = update_surge(&st).await {
                tracing::warn!(error = ?err, "surge update failed");
            }
        }
    });
}

async fn update_surge(st: &AppState) -> Result<(), ApiError> {
    let mut redis = st.redis.clone();
    let now = chrono::Utc::now().timestamp() / 60;
    let minutes: Vec<i64> = (0..SURGE_WINDOW_MINUTES).map(|m| now - m).collect();
    let set_keys: Vec<String> = minutes.iter().map(|m| demand_cells_key(*m)).collect();
    let active: Vec<String> = redis.sunion(&set_keys).await?;
    for raw in active {
        let Ok(cell) = raw.parse::<CellIndex>() else { continue };
        let ring: Vec<CellIndex> = cell.grid_disk(1);
        let keys: Vec<String> = ring
            .iter()
            .flat_map(|c| minutes.iter().map(move |m| format!("nova:demand:{c}:{m}")))
            .collect();
        let counts: Vec<Option<i64>> = redis::cmd("MGET").arg(&keys).query_async(&mut redis).await?;
        let requests: i64 = counts.into_iter().flatten().sum();

        let centre = LatLng::from(cell);
        let nearby: Vec<String> = redis::cmd("GEOSEARCH")
            .arg(crate::drivers::ONLINE_KEY)
            .arg("FROMLONLAT").arg(centre.lng()).arg(centre.lat())
            .arg("BYRADIUS").arg(SUPPLY_RADIUS_KM).arg("km")
            .query_async(&mut redis)
            .await?;
        let free = crate::drivers::count_free(st, &nearby).await?;

        let cap = match super::area_at(st, super::LatLng { lat: centre.lat(), lng: centre.lng() }).await? {
            Some(area) => area.max_surge,
            None => continue,
        };
        let key = format!("nova:surge:{cell}");
        let previous = surge_multiplier(&mut redis, cell).await?;
        let next = surge_next(previous, surge_target(requests, free), cap);
        if next > 1.0 {
            let _: () = redis.set_ex(&key, next.to_string(), SURGE_TTL_SECS).await?;
        } else {
            let _: () = redis.del(&key).await?;
        }
        if (next - previous).abs() >= 0.05 {
            tracing::info!(%cell, requests, free, surge = next, "surge changed");
        }
    }
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
    fn surge_follows_demand_and_eases_down() {
        assert_eq!(surge_target(3, 5), 1.0);
        assert_eq!(surge_target(10, 5), 1.3);
        assert_eq!(surge_target(20, 5), 1.7);
        // Rises at once, capped by the area (2x in India).
        assert_eq!(surge_next(1.0, 2.6, 2.0), 2.0);
        // Falls one step a minute.
        assert_eq!(surge_next(1.8, 1.0, 2.0), 1.7);
        assert_eq!(surge_next(1.05, 1.0, 2.0), 1.0);
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
