//! Ride booking: turn a locked quote into a ride, follow it, cancel it, list past rides.
//!
//! Upfront pricing (the Uber/Bolt model): the price comes from the quote the server stored in
//! Redis (`geo::quote`), never from the app. Booking reads that quote, checks it belongs to the
//! rider, copies route and fare into Postgres, then deletes the quote so it can't book twice
//! (the `quote_id UNIQUE` column backs that up if two requests race).
//!
//! Matching is done by `dispatch` (one task per requested ride, offers to the nearest drivers).
//! It ends unmatched requests as `no_drivers`; [expire_unmatched] is only a safety net for a
//! request whose dispatch task was lost (it runs well after dispatch's own timeout).

use axum::{
    extract::{Path, Query, State},
    http::HeaderMap,
    routing::{get, post},
    Json, Router,
};
use chrono::{DateTime, Duration, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use std::collections::HashMap;
use rand::RngExt;
use uuid::Uuid;

use crate::{
    account::notify,
    auth::{canonical_phone, require_user_id},
    dispatch, drivers,
    error::{is_unique_violation, ApiError},
    geo::{consume_quote, load_quote, rate_limit},
    AppState,
};

/// Safety net only: dispatch itself gives up at DISPATCH_TIMEOUT_SECS and tells the rider.
const NO_DRIVER_TIMEOUT_SECS: f64 = (dispatch::DISPATCH_TIMEOUT_SECS + dispatch::OFFER_TTL_SECS + 30) as f64;
/// Scheduled rides must be at least this far ahead (time to assign a driver) ...
const SCHEDULE_MIN_LEAD_MINUTES: i64 = 15;
/// ... and at most this far (prices and supply further out are guesses).
const SCHEDULE_MAX_DAYS: i64 = 30;
/// Statuses that count as "a ride in progress" (matches index `rides_one_active_per_rider`).
const ACTIVE_STATUSES: &str = "('requested', 'accepted', 'arriving', 'in_progress')";

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/rides", post(book).get(list))
        .route("/v1/rides/active", get(active))
        .route("/v1/rides/passengers", get(recent_passengers))
        .route("/v1/rides/for-me", get(rides_for_me))
        .route("/v1/rides/{id}", get(get_ride))
        .route("/v1/rides/{id}/cancel", post(cancel))
        .route("/v1/track/{token}", get(track_by_token))
}

// ---------- shapes ----------

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RideStop {
    pub name: String,
    pub lat: f64,
    pub lng: f64,
}

#[derive(Serialize)]
pub struct RidePlace {
    name: String,
    address: String,
    lat: f64,
    lng: f64,
}

#[derive(Serialize)]
pub struct Passenger {
    name: String,
    phone: String,
    /// They have a Nova account: the ride is in their app. False: the booker shares `track_url`.
    on_nova: bool,
}

#[derive(Serialize)]
pub struct Ride {
    id: Uuid,
    share_token: String,
    /// Public live-tracking link for this ride (opens the app if installed, else the web page).
    track_url: String,
    status: String,
    product_id: String,
    product_name: String,
    currency: String,
    fare_cents: i64,
    surge_multiplier: f64,
    distance_meters: i64,
    duration_seconds: i64,
    polyline: String,
    pickup: RidePlace,
    dropoff: RidePlace,
    stops: Vec<RideStop>,
    payment_method: String,
    passenger: Option<Passenger>,
    /// Set once a driver accepts.
    driver: Option<RideDriver>,
    /// Tell this to the driver at pickup; they need it to start the trip. Only while active.
    trip_pin: Option<String>,
    scheduled_at: Option<DateTime<Utc>>,
    requested_at: Option<DateTime<Utc>>,
    accepted_at: Option<DateTime<Utc>>,
    arrived_at: Option<DateTime<Utc>>,
    started_at: Option<DateTime<Utc>>,
    ended_at: Option<DateTime<Utc>>,
    cancel_reason: Option<String>,
    created_at: DateTime<Utc>,
    /// The price shown before booking. `fare_cents` is what the rider pays in the end.
    upfront_fare_cents: Option<i64>,
    /// "upfront", or "actual" when the trip changed and was repriced on actual time and distance.
    fare_basis: String,
    /// Why it was repriced: "ended_away" or "longer_trip".
    fare_change_reason: Option<String>,
    wait_fee_cents: i64,
    cancel_fee_cents: i64,
    /// While a driver is on the way: cancelling is free until this moment.
    cancel_free_until: Option<DateTime<Utc>>,
    /// The fee a cancellation after `cancel_free_until` costs (0 when none applies).
    cancel_fee_preview_cents: i64,
}

#[derive(Serialize)]
pub struct RideDriver {
    first_name: String,
    /// Only while the ride is active, so the rider can call about the pickup.
    phone: Option<String>,
    vehicle: RideVehicle,
    /// Last reported position while the ride is active (None if the app went quiet).
    location: Option<DriverLocation>,
}

#[derive(Serialize)]
pub struct RideVehicle {
    make: String,
    model: String,
    color: String,
    plate: String,
}

#[derive(Serialize)]
pub struct DriverLocation {
    lat: f64,
    lng: f64,
}

#[derive(FromRow)]
struct RideRow {
    id: Uuid,
    share_token: String,
    status: String,
    product_id: String,
    product_name: String,
    currency: String,
    fare_cents: i64,
    surge_multiplier: f64,
    distance_meters: i64,
    duration_seconds: i64,
    polyline: String,
    pickup_name: String,
    pickup_address: String,
    pickup_lat: f64,
    pickup_lng: f64,
    dropoff_name: String,
    dropoff_address: String,
    dropoff_lat: f64,
    dropoff_lng: f64,
    payment_method: String,
    passenger_name: Option<String>,
    passenger_phone: Option<String>,
    passenger_user_id: Option<Uuid>,
    driver_id: Option<Uuid>,
    trip_pin: Option<String>,
    driver_first_name: Option<String>,
    driver_phone: Option<String>,
    vehicle_make: Option<String>,
    vehicle_model: Option<String>,
    vehicle_color: Option<String>,
    vehicle_plate: Option<String>,
    scheduled_at: Option<DateTime<Utc>>,
    requested_at: Option<DateTime<Utc>>,
    accepted_at: Option<DateTime<Utc>>,
    arrived_at: Option<DateTime<Utc>>,
    started_at: Option<DateTime<Utc>>,
    ended_at: Option<DateTime<Utc>>,
    cancel_reason: Option<String>,
    created_at: DateTime<Utc>,
    upfront_fare_cents: Option<i64>,
    fare_basis: String,
    fare_change_reason: Option<String>,
    wait_fee_cents: i64,
    cancel_fee_cents: i64,
    cancel_free_until: Option<DateTime<Utc>>,
    cancel_fee_preview_cents: Option<i64>,
}

fn is_active(status: &str) -> bool {
    matches!(status, "accepted" | "arriving" | "in_progress")
}

impl RideRow {
    fn into_ride(self, stops: Vec<RideStop>, web: &str) -> Ride {
        let active = is_active(&self.status);
        Ride {
            id: self.id,
            track_url: format!("{web}/track/{}", self.share_token),
            share_token: self.share_token,
            status: self.status,
            product_id: self.product_id,
            product_name: self.product_name,
            currency: self.currency,
            fare_cents: self.fare_cents,
            surge_multiplier: self.surge_multiplier,
            distance_meters: self.distance_meters,
            duration_seconds: self.duration_seconds,
            polyline: self.polyline,
            pickup: RidePlace { name: self.pickup_name, address: self.pickup_address, lat: self.pickup_lat, lng: self.pickup_lng },
            dropoff: RidePlace { name: self.dropoff_name, address: self.dropoff_address, lat: self.dropoff_lat, lng: self.dropoff_lng },
            stops,
            payment_method: self.payment_method,
            passenger: match (self.passenger_name, self.passenger_phone) {
                (Some(name), Some(phone)) => Some(Passenger { name, phone, on_nova: self.passenger_user_id.is_some() }),
                _ => None,
            },
            driver: match (self.driver_first_name, self.vehicle_make, self.vehicle_model, self.vehicle_color, self.vehicle_plate) {
                (Some(first_name), Some(make), Some(model), Some(color), Some(plate)) => Some(RideDriver {
                    first_name,
                    phone: if active { self.driver_phone } else { None },
                    vehicle: RideVehicle { make, model, color, plate },
                    location: None,
                }),
                _ => None,
            },
            trip_pin: if active { self.trip_pin } else { None },
            scheduled_at: self.scheduled_at,
            requested_at: self.requested_at,
            accepted_at: self.accepted_at,
            arrived_at: self.arrived_at,
            started_at: self.started_at,
            ended_at: self.ended_at,
            cancel_reason: self.cancel_reason,
            created_at: self.created_at,
            upfront_fare_cents: self.upfront_fare_cents,
            fare_basis: self.fare_basis,
            fare_change_reason: self.fare_change_reason,
            wait_fee_cents: self.wait_fee_cents,
            cancel_fee_cents: self.cancel_fee_cents,
            cancel_free_until: self.cancel_free_until,
            cancel_fee_preview_cents: self.cancel_fee_preview_cents.unwrap_or(0),
        }
    }
}

#[derive(FromRow)]
struct RideStopRow {
    ride_id: Uuid,
    name: String,
    lat: f64,
    lng: f64,
}

async fn attach_stops(st: &AppState, rows: Vec<RideRow>) -> Result<Vec<Ride>, ApiError> {
    if rows.is_empty() {
        return Ok(Vec::new());
    }
    let ids: Vec<Uuid> = rows.iter().map(|r| r.id).collect();
    let stop_rows = sqlx::query_as::<_, RideStopRow>(
        "SELECT ride_id, name, lat, lng FROM ride_stops WHERE ride_id = ANY($1) ORDER BY ride_id, stop_order",
    )
    .bind(&ids)
    .fetch_all(&st.pool)
    .await?;
    let mut by_ride: HashMap<Uuid, Vec<RideStop>> = HashMap::new();
    for s in stop_rows {
        by_ride.entry(s.ride_id).or_default().push(RideStop { name: s.name, lat: s.lat, lng: s.lng });
    }
    let mut rides = Vec::with_capacity(rows.len());
    for r in rows {
        let stops = by_ride.remove(&r.id).unwrap_or_default();
        let live = r.driver_id.filter(|_| is_active(&r.status));
        let mut ride = r.into_ride(stops, &st.cfg.public_web_url);
        if let (Some(driver_id), Some(driver)) = (live, ride.driver.as_mut()) {
            driver.location = drivers::last_location(st, driver_id).await.map(|(lat, lng)| DriverLocation { lat, lng });
        }
        rides.push(ride);
    }
    Ok(rides)
}

/// Every read selects the same columns, joined to the product for its display name.
const RIDE_SELECT: &str = "SELECT r.id, r.share_token, r.status, r.product_id, p.name AS product_name, r.currency, r.fare_cents,
        r.surge_multiplier, r.distance_meters, r.duration_seconds, r.polyline,
        r.pickup_name, r.pickup_address, r.pickup_lat, r.pickup_lng,
        r.dropoff_name, r.dropoff_address, r.dropoff_lat, r.dropoff_lng,
        r.payment_method, r.passenger_name, r.passenger_phone, r.passenger_user_id,
        r.driver_id, r.trip_pin, du.first_name AS driver_first_name,
        CASE WHEN du.phone_verified_at IS NOT NULL THEN du.phone_e164 END AS driver_phone,
        v.make AS vehicle_make, v.model AS vehicle_model, v.color AS vehicle_color, v.plate AS vehicle_plate,
        r.scheduled_at, r.requested_at, r.accepted_at, r.arrived_at, r.started_at,
        r.ended_at, r.cancel_reason, r.created_at,
        r.upfront_fare_cents, r.fare_basis, r.fare_change_reason, r.wait_fee_cents, r.cancel_fee_cents,
        CASE WHEN r.status IN ('accepted', 'arriving') AND c.ride_id IS NOT NULL
             THEN r.accepted_at + make_interval(secs => c.cancel_grace_secs) END AS cancel_free_until,
        -- Same rule as fare::cancellation: the card fee, capped at the area's share of the fare.
        CASE WHEN r.status IN ('accepted', 'arriving') AND c.ride_id IS NOT NULL
             THEN (round(least(c.cancel_fee_cents::numeric,
                               COALESCE(r.upfront_fare_cents, r.fare_cents) * c.cancel_fee_max_bps / 10000.0)
                         / greatest(c.rounding_minor, 1)) * greatest(c.rounding_minor, 1))::bigint END AS cancel_fee_preview_cents
    FROM rides r JOIN ride_products p ON p.id = r.product_id
    LEFT JOIN users du ON du.id = r.driver_id
    LEFT JOIN vehicles v ON v.id = r.vehicle_id
    LEFT JOIN ride_rate_cards c ON c.ride_id = r.id";

async fn fetch_ride(st: &AppState, rider: Uuid, id: Uuid) -> Result<Option<Ride>, ApiError> {
    let row = sqlx::query_as::<_, RideRow>(&format!("{RIDE_SELECT} WHERE r.id = $1 AND r.rider_id = $2"))
        .bind(id)
        .bind(rider)
        .fetch_optional(&st.pool)
        .await?;
    match row {
        Some(r) => Ok(attach_stops(st, vec![r]).await?.into_iter().next()),
        None => Ok(None),
    }
}

/// Ends this rider's requests that waited too long for a driver (see module docs).
async fn expire_unmatched(st: &AppState, rider: Uuid) -> Result<(), ApiError> {
    let expired: Vec<(Uuid, String, String)> = sqlx::query_as(
        "UPDATE rides r SET status = 'no_drivers', ended_at = now(), updated_at = now()
         FROM ride_products p
         WHERE p.id = r.product_id AND r.rider_id = $1 AND r.status = 'requested'
           AND r.requested_at < now() - make_interval(secs => $2)
         RETURNING r.id, p.name, r.dropoff_name",
    )
    .bind(rider)
    .bind(NO_DRIVER_TIMEOUT_SECS)
    .fetch_all(&st.pool)
    .await?;
    for (id, product, dropoff) in expired {
        notify(st, rider, "trip", "no_drivers", &format!("No {product} driver found"),
            &format!("Your ride to {dropoff} wasn't matched. You haven't been charged."), Some(id)).await;
    }
    Ok(())
}

/// Trims and bounds free text from the app (place names are display-only, coordinates come from the quote).
fn clean(text: &str, max: usize) -> String {
    text.trim().chars().take(max).collect()
}

// ---------- book ----------

#[derive(Deserialize)]
pub struct PassengerReq {
    name: String,
    phone: String,
}

#[derive(Deserialize)]
pub struct BookReq {
    quote_id: Uuid,
    product_id: String,
    payment_method: String,
    pickup_name: String,
    #[serde(default)]
    pickup_address: String,
    dropoff_name: String,
    #[serde(default)]
    dropoff_address: String,
    /// Display names for the quote's stops, in the same order.
    #[serde(default)]
    stop_names: Vec<String>,
    /// Set to book for later instead of now.
    scheduled_at: Option<DateTime<Utc>>,
    /// Set when booking for someone else.
    passenger: Option<PassengerReq>,
}

async fn book(
    State(st): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<BookReq>,
) -> Result<Json<Ride>, ApiError> {
    let rider = require_user_id(&st, &headers)?;
    rate_limit(&st, "book", rider, 10, 60).await?;

    if !matches!(req.payment_method.as_str(), "gpay" | "card" | "credit") {
        return Err(ApiError::BadRequest("invalid_payment_method"));
    }
    let pickup_name = clean(&req.pickup_name, 200);
    let dropoff_name = clean(&req.dropoff_name, 200);
    if pickup_name.is_empty() || dropoff_name.is_empty() {
        return Err(ApiError::BadRequest("invalid_place"));
    }

    let passenger = match &req.passenger {
        None => None,
        Some(p) => {
            let name = clean(&p.name, 80);
            if name.is_empty() {
                return Err(ApiError::BadRequest("invalid_passenger_name"));
            }
            let phone = canonical_phone(&p.phone).ok_or(ApiError::BadRequest("invalid_phone"))?;
            Some((name, phone))
        }
    };

    if let Some(at) = req.scheduled_at {
        let now = Utc::now();
        if at < now + Duration::minutes(SCHEDULE_MIN_LEAD_MINUTES) {
            return Err(ApiError::BadRequest("schedule_too_soon"));
        }
        if at > now + Duration::days(SCHEDULE_MAX_DAYS) {
            return Err(ApiError::BadRequest("schedule_too_far"));
        }
    }

    // Price, route and area come from the server-side quote only.
    let quote = load_quote(&st, rider, req.quote_id).await?;
    let option = quote
        .options
        .iter()
        .find(|o| o.product_id == req.product_id)
        .ok_or(ApiError::BadRequest("invalid_product"))?;
    let stops: Vec<RideStop> = quote
        .stops
        .iter()
        .enumerate()
        .map(|(i, p)| RideStop {
            name: req.stop_names.get(i).map(|n| clean(n, 200)).filter(|n| !n.is_empty()).unwrap_or_else(|| format!("Stop {}", i + 1)),
            lat: p.lat,
            lng: p.lng,
        })
        .collect();

    // A stale request from earlier shouldn't block booking again.
    expire_unmatched(&st, rider).await?;

    let id = Uuid::now_v7();
    let share_token = Uuid::new_v4().simple().to_string();
    let status = if req.scheduled_at.is_some() { "scheduled" } else { "requested" };
    let mut tx = st.pool.begin().await?;
    let inserted = sqlx::query(
        "INSERT INTO rides (
            id, share_token, rider_id, quote_id, service_area_id, product_id, status,
            pickup_name, pickup_address, pickup_lat, pickup_lng, pickup,
            dropoff_name, dropoff_address, dropoff_lat, dropoff_lng,
            currency, fare_cents, upfront_fare_cents, surge_multiplier, distance_meters, duration_seconds, polyline,
            payment_method, passenger_name, passenger_phone, scheduled_at, requested_at, trip_pin)
         VALUES ($1, $2, $3, $4, $5, $6, $7,
            $8, $9, $10, $11, ST_SetSRID(ST_MakePoint($11, $10), 4326)::geography,
            $12, $13, $14, $15,
            $16, $17, $17, $18, $19, $20, $21,
            $22, $23, $24, $25, CASE WHEN $7 = 'requested' THEN now() END, $26)",
    )
    .bind(id)
    .bind(&share_token)
    .bind(rider)
    .bind(req.quote_id)
    .bind(quote.service_area_id)
    .bind(&option.product_id)
    .bind(status)
    .bind(&pickup_name)
    .bind(clean(&req.pickup_address, 300))
    .bind(quote.pickup.lat)
    .bind(quote.pickup.lng)
    .bind(&dropoff_name)
    .bind(clean(&req.dropoff_address, 300))
    .bind(quote.dropoff.lat)
    .bind(quote.dropoff.lng)
    .bind(&quote.currency)
    .bind(option.fare_cents)
    .bind(quote.surge_multiplier)
    .bind(quote.distance_meters)
    .bind(quote.duration_seconds)
    .bind(&quote.polyline)
    .bind(&req.payment_method)
    .bind(passenger.as_ref().map(|p| p.0.as_str()))
    .bind(passenger.as_ref().map(|p| p.1.as_str()))
    .bind(req.scheduled_at)
    .bind(format!("{:04}", rand::rng().random_range(0..10_000u32)))
    .execute(&mut *tx)
    .await;

    if let Err(err) = inserted {
        if is_unique_violation(&err) {
            // Either this quote was already booked or the rider already has a ride in progress.
            let constraint = match &err {
                sqlx::Error::Database(db) => db.constraint().unwrap_or_default().to_string(),
                _ => String::new(),
            };
            return Err(ApiError::Conflict(if constraint == "rides_one_active_per_rider" {
                "ride_in_progress"
            } else {
                "quote_already_booked"
            }));
        }
        return Err(err.into());
    }
    // The rate card this ride is settled on, fixed at booking.
    crate::fare::snapshot(&mut *tx, id).await?;
    for (idx, stop) in stops.iter().enumerate() {
        sqlx::query(
            "INSERT INTO ride_stops (ride_id, stop_order, name, lat, lng) VALUES ($1, $2, $3, $4, $5)",
        )
        .bind(id)
        .bind(idx as i16)
        .bind(&stop.name)
        .bind(stop.lat)
        .bind(stop.lng)
        .execute(&mut *tx)
        .await?;
    }
    tx.commit().await?;
    consume_quote(&st, req.quote_id).await?;

    tracing::info!(ride = %id, %rider, status, "ride booked");
    if status == "requested" {
        dispatch::spawn(&st, id);
    }
    if let Some((_, phone)) = &passenger {
        notify_passenger(&st, id, rider, quote.service_area_id, phone, &option.name, &pickup_name, &dropoff_name, req.scheduled_at).await;
    }
    if req.scheduled_at.is_some() {
        // Times are formatted by the app in the rider's zone; the body names the ride, not the time.
        notify(&st, rider, "trip", "ride_scheduled", "Ride scheduled",
            &format!("{} to {} · fare locked", option.name, dropoff_name), Some(id)).await;
    }
    fetch_ride(&st, rider, id).await?.map(Json).ok_or(ApiError::Internal)
}

// ---------- read ----------

async fn get_ride(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
) -> Result<Json<Ride>, ApiError> {
    let rider = require_user_id(&st, &headers)?;
    expire_unmatched(&st, rider).await?;
    fetch_ride(&st, rider, id).await?.map(Json).ok_or(ApiError::NotFound("ride_not_found"))
}

#[derive(Serialize)]
pub struct ActiveRes {
    ride: Option<Ride>,
}

/// The rider's ride in progress, if any (the app resumes it after a restart).
async fn active(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<ActiveRes>, ApiError> {
    let rider = require_user_id(&st, &headers)?;
    expire_unmatched(&st, rider).await?;
    let row = sqlx::query_as::<_, RideRow>(&format!(
        "{RIDE_SELECT} WHERE r.rider_id = $1 AND r.status IN {ACTIVE_STATUSES} LIMIT 1"
    ))
    .bind(rider)
    .fetch_optional(&st.pool)
    .await?;
    let ride = match row {
        Some(r) => attach_stops(&st, vec![r]).await?.into_iter().next(),
        None => None,
    };
    Ok(Json(ActiveRes { ride }))
}

#[derive(Deserialize)]
pub struct ListQuery {
    limit: Option<i64>,
    /// Keyset pagination: pass the `created_at` of the last ride you have.
    before: Option<DateTime<Utc>>,
}

#[derive(Serialize)]
pub struct ListRes {
    rides: Vec<Ride>,
}

/// Activity: newest first, keyset paginated (index `rides_rider_recent`), never an OFFSET scan.
async fn list(
    State(st): State<AppState>,
    headers: HeaderMap,
    Query(q): Query<ListQuery>,
) -> Result<Json<ListRes>, ApiError> {
    let rider = require_user_id(&st, &headers)?;
    expire_unmatched(&st, rider).await?;
    let limit = q.limit.unwrap_or(20).clamp(1, 50);
    let rows = sqlx::query_as::<_, RideRow>(&format!(
        "{RIDE_SELECT} WHERE r.rider_id = $1 AND ($2::timestamptz IS NULL OR r.created_at < $2)
         ORDER BY r.created_at DESC LIMIT $3"
    ))
    .bind(rider)
    .bind(q.before)
    .bind(limit)
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(ListRes { rides: attach_stops(&st, rows).await? }))
}

// ---------- cancel ----------

#[derive(Deserialize, Default)]
pub struct CancelReq {
    reason: Option<String>,
}

async fn cancel(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
    body: Option<Json<CancelReq>>,
) -> Result<Json<Ride>, ApiError> {
    let rider = require_user_id(&st, &headers)?;
    let reason = body.and_then(|Json(b)| b.reason).map(|r| clean(&r, 200)).filter(|r| !r.is_empty());
    let (fee, service, earned) = cancellation_fee(&st, rider, id).await?;
    // Only rides that haven't started can be cancelled by the rider; the WHERE makes it atomic.
    // The driver keeps their link to the ride (so it's in their history) but is free again.
    let updated: Option<Option<Uuid>> = sqlx::query_scalar(
        "UPDATE rides SET status = 'cancelled', cancel_reason = $3, ended_at = now(), updated_at = now(),
                cancel_fee_cents = $4, service_fee_cents = CASE WHEN $4 > 0 THEN $5 END,
                driver_earnings_cents = CASE WHEN $4 > 0 THEN $6 END
         WHERE id = $1 AND rider_id = $2 AND status IN ('scheduled', 'requested', 'accepted', 'arriving')
         RETURNING driver_id",
    )
    .bind(id)
    .bind(rider)
    .bind(reason)
    .bind(fee)
    .bind(service)
    .bind(earned)
    .fetch_optional(&st.pool)
    .await?;
    let ride = fetch_ride(&st, rider, id).await?.ok_or(ApiError::NotFound("ride_not_found"))?;
    if updated.is_none() && ride.status != "cancelled" {
        return Err(ApiError::Conflict("ride_not_cancellable"));
    }
    if let Some(driver) = updated {
        dispatch::rider_cancelled(&st, id, driver).await;
        let charged = if fee > 0 {
            "a cancellation fee applies because your driver was already on the way"
        } else {
            "you haven't been charged"
        };
        notify(&st, rider, "trip", "ride_cancelled", "Ride cancelled",
            &format!("{} to {} · {charged}", ride.product_name, ride.dropoff.name), Some(id)).await;
        // The Nova user it was booked for hears about it too.
        let passenger: Option<Uuid> = sqlx::query_scalar("SELECT passenger_user_id FROM rides WHERE id = $1")
            .bind(id)
            .fetch_one(&st.pool)
            .await
            .unwrap_or(None);
        if let Some(passenger) = passenger {
            notify(&st, passenger, "trip", "ride_cancelled", "Your ride was cancelled",
                &format!("The ride to {} booked for you was cancelled", ride.dropoff.name), Some(id)).await;
        }
    }
    Ok(Json(ride))
}

/// Uber's rule: cancelling is free until the grace period after a driver accepts. After it, the
/// rider pays the cancellation fee if the driver is waiting at the pickup or has been making
/// progress towards it (closer now than when they got the offer). Returns (fee, service fee,
/// driver's share), all zero when free.
async fn cancellation_fee(st: &AppState, rider: Uuid, ride: Uuid) -> Result<(i64, i64, i64), ApiError> {
    let row: Option<(String, Option<Uuid>, Option<i64>, i64, Option<i32>)> = sqlx::query_as(
        "SELECT r.status, r.driver_id, extract(epoch FROM now() - r.accepted_at)::bigint,
                COALESCE(r.upfront_fare_cents, r.fare_cents),
                (SELECT o.distance_meters FROM ride_offers o
                 WHERE o.ride_id = r.id AND o.driver_id = r.driver_id AND o.response = 'accepted' LIMIT 1)
         FROM rides r WHERE r.id = $1 AND r.rider_id = $2",
    )
    .bind(ride)
    .bind(rider)
    .fetch_optional(&st.pool)
    .await?;
    let Some((status, Some(driver), Some(since_accept), upfront, offered_m)) = row else {
        return Ok((0, 0, 0));
    };
    if !matches!(status.as_str(), "accepted" | "arriving") {
        return Ok((0, 0, 0));
    }
    let card = crate::fare::card(&st.pool, ride).await?;
    if since_accept < card.cancel_grace_secs as i64 {
        return Ok((0, 0, 0));
    }
    let progressing = status == "arriving" || match (crate::drivers::last_location(st, driver).await, offered_m) {
        (Some((lat, lng)), Some(offered)) => {
            let now_m: Option<i32> = sqlx::query_scalar(
                "SELECT ST_Distance(pickup, ST_SetSRID(ST_MakePoint($2, $1), 4326)::geography)::int FROM rides WHERE id = $3",
            )
            .bind(lat)
            .bind(lng)
            .bind(ride)
            .fetch_optional(&st.pool)
            .await?;
            now_m.is_some_and(|m| m < offered)
        }
        _ => false,
    };
    Ok(if progressing { crate::fare::cancellation(&card, upfront) } else { (0, 0, 0) })
}

// ---------- booking for someone else ----------

/// Tells the person riding, when they have a Nova account, that a ride was booked for them
/// (in-app inbox now; the same event goes over the live WebSocket channel once it exists).
/// Links the ride to them so it can appear in their Activity. Never fails the booking.
#[allow(clippy::too_many_arguments)]
async fn notify_passenger(
    st: &AppState,
    ride: Uuid,
    rider: Uuid,
    area: Uuid,
    phone: &str,
    product: &str,
    pickup: &str,
    dropoff: &str,
    scheduled_at: Option<DateTime<Utc>>,
) {
    let found: Option<(Uuid, String, String)> = sqlx::query_as(
        "WITH p AS (
            UPDATE rides SET passenger_user_id = u.id
            FROM users u
            WHERE rides.id = $1 AND u.phone_e164 = $2 AND u.phone_verified_at IS NOT NULL
              AND u.deletion_requested_at IS NULL AND u.id <> $3
            RETURNING u.id
         )
         SELECT p.id, b.first_name, a.timezone FROM p, users b, service_areas a WHERE b.id = $3 AND a.id = $4",
    )
    .bind(ride)
    .bind(phone)
    .bind(rider)
    .bind(area)
    .fetch_optional(&st.pool)
    .await
    .unwrap_or_else(|err| {
        tracing::warn!(error = ?err, %ride, "could not link passenger");
        None
    });
    let Some((passenger, booker, timezone)) = found else { return };
    // Scheduled times in the city's zone (where the ride happens), e.g. "Tue 3 Dec, 6:30 PM".
    let when = scheduled_at.map(|at| {
        let tz: chrono_tz::Tz = timezone.parse().unwrap_or(chrono_tz::UTC);
        at.with_timezone(&tz).format("%a %-d %b, %-I:%M %p").to_string()
    });
    let title = match &when {
        Some(when) => format!("{booker} booked you a ride for {when}"),
        None => format!("{booker} booked you a ride"),
    };
    notify(st, passenger, "trip", "booked_for_you", &title,
        &format!("{product} · {} to {}", clean(pickup, 60), clean(dropoff, 60)), Some(ride)).await;
}

#[derive(Serialize)]
pub struct TrackRideRes {
    status: String,
    product_name: String,
    booker_name: String,
    passenger_name: Option<String>,
    pickup: RidePlace,
    dropoff: RidePlace,
    stops: Vec<RideStop>,
    polyline: String,
    distance_meters: i64,
    duration_seconds: i64,
    scheduled_at: Option<DateTime<Utc>>,
    requested_at: Option<DateTime<Utc>>,
    ended_at: Option<DateTime<Utc>>,
}

#[derive(FromRow)]
struct TrackRideRow {
    id: Uuid,
    status: String,
    product_name: String,
    booker_name: String,
    passenger_name: Option<String>,
    pickup_name: String,
    pickup_address: String,
    pickup_lat: f64,
    pickup_lng: f64,
    dropoff_name: String,
    dropoff_address: String,
    dropoff_lat: f64,
    dropoff_lng: f64,
    polyline: String,
    distance_meters: i64,
    duration_seconds: i64,
    scheduled_at: Option<DateTime<Utc>>,
    requested_at: Option<DateTime<Utc>>,
    ended_at: Option<DateTime<Utc>>,
}

/// Public ride-tracking endpoint for recipients of "For someone" SMS links.
async fn track_by_token(
    State(st): State<AppState>,
    Path(token): Path<String>,
) -> Result<Json<TrackRideRes>, ApiError> {
    let row = sqlx::query_as::<_, TrackRideRow>(
        "SELECT r.id, r.status, p.name AS product_name, u.first_name AS booker_name, r.passenger_name,
                r.pickup_name, r.pickup_address, r.pickup_lat, r.pickup_lng,
                r.dropoff_name, r.dropoff_address, r.dropoff_lat, r.dropoff_lng,
                r.polyline, r.distance_meters, r.duration_seconds,
                r.scheduled_at, r.requested_at, r.ended_at
         FROM rides r
         JOIN ride_products p ON p.id = r.product_id
         JOIN users u ON u.id = r.rider_id
         WHERE r.share_token = $1",
    )
    .bind(token.trim())
    .fetch_optional(&st.pool)
    .await?
    .ok_or(ApiError::NotFound("ride_not_found"))?;

    let stops = sqlx::query_as::<_, RideStopRow>(
        "SELECT ride_id, name, lat, lng FROM ride_stops WHERE ride_id = $1 ORDER BY stop_order",
    )
    .bind(row.id)
    .fetch_all(&st.pool)
    .await?
    .into_iter()
    .map(|s| RideStop { name: s.name, lat: s.lat, lng: s.lng })
    .collect();

    Ok(Json(TrackRideRes {
        status: row.status,
        product_name: row.product_name,
        booker_name: row.booker_name,
        passenger_name: row.passenger_name,
        pickup: RidePlace { name: row.pickup_name, address: row.pickup_address, lat: row.pickup_lat, lng: row.pickup_lng },
        dropoff: RidePlace { name: row.dropoff_name, address: row.dropoff_address, lat: row.dropoff_lat, lng: row.dropoff_lng },
        stops,
        polyline: row.polyline,
        distance_meters: row.distance_meters,
        duration_seconds: row.duration_seconds,
        scheduled_at: row.scheduled_at,
        requested_at: row.requested_at,
        ended_at: row.ended_at,
    }))
}

#[derive(Serialize, FromRow)]
pub struct RecentPassenger {
    name: String,
    phone: String,
}

#[derive(Serialize)]
pub struct PassengersRes {
    passengers: Vec<RecentPassenger>,
}

/// People this rider booked for before, most recent first, so they're one tap next time.
/// Derived from past rides, so there is no extra list to keep in sync (or to export/delete).
async fn recent_passengers(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<PassengersRes>, ApiError> {
    let rider = require_user_id(&st, &headers)?;
    let passengers = sqlx::query_as::<_, RecentPassenger>(
        // Latest name per number, then the 5 most recently used numbers.
        "SELECT name, phone FROM (
            SELECT DISTINCT ON (passenger_phone) passenger_name AS name, passenger_phone AS phone, created_at
            FROM rides WHERE rider_id = $1 AND passenger_phone IS NOT NULL
            ORDER BY passenger_phone, created_at DESC
         ) latest ORDER BY created_at DESC LIMIT 5",
    )
    .bind(rider)
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(PassengersRes { passengers }))
}

// ---------- rides other people booked for me ----------

/// A ride someone else booked with this rider as the passenger. Read-only for the passenger:
/// the booker pays, changes and cancels; the passenger follows it (track_url / TrackRide).
#[derive(Serialize, FromRow)]
pub struct RideForMe {
    id: Uuid,
    status: String,
    product_name: String,
    booker_name: String,
    pickup_name: String,
    dropoff_name: String,
    scheduled_at: Option<DateTime<Utc>>,
    requested_at: Option<DateTime<Utc>>,
    created_at: DateTime<Utc>,
    share_token: String,
}

#[derive(Serialize)]
pub struct RidesForMeRes {
    rides: Vec<RideForMe>,
}

/// Newest first, last 30 days, via index `rides_passenger_user`.
async fn rides_for_me(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<RidesForMeRes>, ApiError> {
    let me = require_user_id(&st, &headers)?;
    let rides = sqlx::query_as::<_, RideForMe>(
        "SELECT r.id, r.status, p.name AS product_name, u.first_name AS booker_name,
                r.pickup_name, r.dropoff_name, r.scheduled_at, r.requested_at, r.created_at, r.share_token
         FROM rides r
         JOIN ride_products p ON p.id = r.product_id
         JOIN users u ON u.id = r.rider_id
         WHERE r.passenger_user_id = $1 AND r.created_at > now() - interval '30 days'
         ORDER BY r.created_at DESC LIMIT 20",
    )
    .bind(me)
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(RidesForMeRes { rides }))
}
