//! Ride booking: turn a locked quote into a ride, follow it, cancel it, list past rides.
//!
//! Upfront pricing (the Uber/Bolt model): the price comes from the quote the server stored in
//! Redis (`geo::quote`), never from the app. Booking reads that quote, checks it belongs to the
//! rider, copies route and fare into Postgres, then deletes the quote so it can't book twice
//! (the `quote_id UNIQUE` column backs that up if two requests race).
//!
//! Matching drivers needs the driver app online (next phase). Until then a request that nobody
//! accepts within [NO_DRIVER_TIMEOUT_SECS] honestly ends as `no_drivers`; nothing is faked. The
//! timeout is applied lazily when the ride is read, so there is no background job to run.
//! When dispatch lands, it moves `requested -> accepted` and promotes due `scheduled` rides.

use axum::{
    extract::{Path, Query, State},
    http::HeaderMap,
    routing::{get, post},
    Json, Router,
};
use chrono::{DateTime, Duration, Utc};
use serde::{Deserialize, Serialize};
use sqlx::{types::Json as DbJson, FromRow};
use uuid::Uuid;

use crate::{
    account::notify,
    auth::{canonical_phone, require_user_id},
    error::{is_unique_violation, ApiError},
    geo::{consume_quote, load_quote, rate_limit},
    AppState,
};

/// How long a request waits for a driver before ending as `no_drivers`.
const NO_DRIVER_TIMEOUT_SECS: f64 = 120.0;
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
        .route("/v1/rides/{id}", get(get_ride))
        .route("/v1/rides/{id}/cancel", post(cancel))
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
}

#[derive(Serialize)]
pub struct Ride {
    id: Uuid,
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
    scheduled_at: Option<DateTime<Utc>>,
    requested_at: Option<DateTime<Utc>>,
    ended_at: Option<DateTime<Utc>>,
    cancel_reason: Option<String>,
    created_at: DateTime<Utc>,
}

#[derive(FromRow)]
struct RideRow {
    id: Uuid,
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
    stops: DbJson<Vec<RideStop>>,
    payment_method: String,
    passenger_name: Option<String>,
    passenger_phone: Option<String>,
    scheduled_at: Option<DateTime<Utc>>,
    requested_at: Option<DateTime<Utc>>,
    ended_at: Option<DateTime<Utc>>,
    cancel_reason: Option<String>,
    created_at: DateTime<Utc>,
}

impl From<RideRow> for Ride {
    fn from(r: RideRow) -> Self {
        Ride {
            id: r.id,
            status: r.status,
            product_id: r.product_id,
            product_name: r.product_name,
            currency: r.currency,
            fare_cents: r.fare_cents,
            surge_multiplier: r.surge_multiplier,
            distance_meters: r.distance_meters,
            duration_seconds: r.duration_seconds,
            polyline: r.polyline,
            pickup: RidePlace { name: r.pickup_name, address: r.pickup_address, lat: r.pickup_lat, lng: r.pickup_lng },
            dropoff: RidePlace { name: r.dropoff_name, address: r.dropoff_address, lat: r.dropoff_lat, lng: r.dropoff_lng },
            stops: r.stops.0,
            payment_method: r.payment_method,
            passenger: match (r.passenger_name, r.passenger_phone) {
                (Some(name), Some(phone)) => Some(Passenger { name, phone }),
                _ => None,
            },
            scheduled_at: r.scheduled_at,
            requested_at: r.requested_at,
            ended_at: r.ended_at,
            cancel_reason: r.cancel_reason,
            created_at: r.created_at,
        }
    }
}

/// Every read selects the same columns, joined to the product for its display name.
const RIDE_SELECT: &str = "SELECT r.id, r.status, r.product_id, p.name AS product_name, r.currency, r.fare_cents,
        r.surge_multiplier, r.distance_meters, r.duration_seconds, r.polyline,
        r.pickup_name, r.pickup_address, r.pickup_lat, r.pickup_lng,
        r.dropoff_name, r.dropoff_address, r.dropoff_lat, r.dropoff_lng, r.stops,
        r.payment_method, r.passenger_name, r.passenger_phone,
        r.scheduled_at, r.requested_at, r.ended_at, r.cancel_reason, r.created_at
    FROM rides r JOIN ride_products p ON p.id = r.product_id";

async fn fetch_ride(st: &AppState, rider: Uuid, id: Uuid) -> Result<Option<Ride>, ApiError> {
    let row = sqlx::query_as::<_, RideRow>(&format!("{RIDE_SELECT} WHERE r.id = $1 AND r.rider_id = $2"))
        .bind(id)
        .bind(rider)
        .fetch_optional(&st.pool)
        .await?;
    Ok(row.map(Ride::from))
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
    let status = if req.scheduled_at.is_some() { "scheduled" } else { "requested" };
    let inserted = sqlx::query(
        "INSERT INTO rides (
            id, rider_id, quote_id, service_area_id, product_id, status,
            pickup_name, pickup_address, pickup_lat, pickup_lng, pickup,
            dropoff_name, dropoff_address, dropoff_lat, dropoff_lng, stops,
            currency, fare_cents, surge_multiplier, distance_meters, duration_seconds, polyline,
            payment_method, passenger_name, passenger_phone, scheduled_at, requested_at)
         VALUES ($1, $2, $3, $4, $5, $6,
            $7, $8, $9, $10, ST_SetSRID(ST_MakePoint($10, $9), 4326)::geography,
            $11, $12, $13, $14, $15,
            $16, $17, $18, $19, $20, $21,
            $22, $23, $24, $25, CASE WHEN $6 = 'requested' THEN now() END)",
    )
    .bind(id)
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
    .bind(DbJson(&stops))
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
    .execute(&st.pool)
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
    consume_quote(&st, req.quote_id).await?;

    tracing::info!(ride = %id, %rider, status, "ride booked");
    if let Some((name, phone)) = &passenger {
        text_passenger(&st, rider, quote.service_area_id, name, phone, &option.name, &pickup_name, &dropoff_name, req.scheduled_at).await;
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
    Ok(Json(ActiveRes { ride: row.map(Ride::from) }))
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
    Ok(Json(ListRes { rides: rows.into_iter().map(Ride::from).collect() }))
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
    // Only rides that haven't started can be cancelled by the rider; the WHERE makes it atomic.
    let updated = sqlx::query(
        "UPDATE rides SET status = 'cancelled', cancel_reason = $3, ended_at = now(), updated_at = now()
         WHERE id = $1 AND rider_id = $2 AND status IN ('scheduled', 'requested', 'accepted', 'arriving')",
    )
    .bind(id)
    .bind(rider)
    .bind(reason)
    .execute(&st.pool)
    .await?;
    let ride = fetch_ride(&st, rider, id).await?.ok_or(ApiError::NotFound("ride_not_found"))?;
    if updated.rows_affected() == 0 && ride.status != "cancelled" {
        return Err(ApiError::Conflict("ride_not_cancellable"));
    }
    if updated.rows_affected() > 0 {
        notify(&st, rider, "trip", "ride_cancelled", "Ride cancelled",
            &format!("{} to {} · you haven't been charged", ride.product_name, ride.dropoff.name), Some(id)).await;
    }
    Ok(Json(ride))
}

// ---------- booking for someone else ----------

/// Most passenger texts a rider can trigger per day. Texts go to numbers the rider types, so this
/// is the guard against using bookings to spam (or to run up SMS costs on premium numbers).
const PASSENGER_TEXTS_PER_DAY: i64 = 5;

/// Tells the person riding that a ride was booked for them (Uber's "Request for someone else").
/// Best effort: runs in the background and never fails the booking.
#[allow(clippy::too_many_arguments)]
async fn text_passenger(
    st: &AppState,
    rider: Uuid,
    area: Uuid,
    name: &str,
    phone: &str,
    product: &str,
    pickup: &str,
    dropoff: &str,
    scheduled_at: Option<DateTime<Utc>>,
) {
    if rate_limit(st, "passenger_text", rider, PASSENGER_TEXTS_PER_DAY, 24 * 3600).await.is_err() {
        tracing::warn!(%rider, "passenger text skipped: daily limit reached");
        return;
    }
    let row: Option<(String, Option<String>, String)> = sqlx::query_as(
        "SELECT u.first_name, u.phone_e164, a.timezone FROM users u, service_areas a WHERE u.id = $1 AND a.id = $2",
    )
    .bind(rider)
    .bind(area)
    .fetch_optional(&st.pool)
    .await
    .ok()
    .flatten();
    let Some((booker, booker_phone, timezone)) = row else { return };
    if booker_phone.as_deref() == Some(phone) {
        return; // Booked "for someone" with their own number: nothing to tell.
    }
    // Times in the city's zone (where the ride happens), e.g. "Tue 3 Dec, 6:30 PM".
    let when = scheduled_at.map(|at| {
        let tz: chrono_tz::Tz = timezone.parse().unwrap_or(chrono_tz::UTC);
        at.with_timezone(&tz).format("%a %-d %b, %-I:%M %p").to_string()
    });
    let short = |s: &str| clean(s, 60);
    let body = match when {
        Some(when) => format!(
            "Hi {}, {} booked you a Nova {} ride for {}.\nPickup: {}\nDrop-off: {}",
            short(name), short(&booker), product, when, short(pickup), short(dropoff)
        ),
        None => format!(
            "Hi {}, {} booked you a Nova {} ride. A driver is being matched now.\nPickup: {}\nDrop-off: {}",
            short(name), short(&booker), product, short(pickup), short(dropoff)
        ),
    };
    let st = st.clone();
    let to = phone.to_string();
    tokio::spawn(async move {
        crate::auth::notify::send_text(&st, &to, &body).await;
    });
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
