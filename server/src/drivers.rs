//! Driver side: becoming a driver, the car, going online, and working a ride.
//!
//! A driver signs in with the same Nova account (phone/email OTP or passkey) and applies here.
//! An admin approves the application (`DRIVER_AUTO_APPROVE=true` skips that in development).
//!
//! Presence lives in Redis, not Postgres, because it changes every few seconds:
//!   * `nova:drivers:online`  GEO set of drivers available for offers (dispatch searches it)
//!   * `nova:driver:{id}:seen` heartbeat with a short TTL; no heartbeat = not really online
//!   * `nova:driver:{id}:loc`  last position, also kept while on a trip (riders see it)
//! The app sends `POST /v1/driver/location` every few seconds while online.
//!
//! Every ride change is a single conditional UPDATE (`WHERE status = ...`), so two taps, two
//! devices or a race with the rider cancelling can never move a ride twice.

use axum::{
    extract::{Path, State},
    http::HeaderMap,
    routing::{get, post, put},
    Json, Router,
};
use chrono::{DateTime, Datelike, NaiveDate, Utc};
use serde::{Deserialize, Serialize};
use serde_json::json;
use sqlx::FromRow;
use uuid::Uuid;

use crate::{
    auth::{load_user, require_user_id, verified_phone, User},
    dispatch,
    error::{is_unique_violation, ApiError},
    fare,
    geo::rate_limit,
    rides::RideStop,
    AppState,
};

pub(crate) const ONLINE_KEY: &str = "nova:drivers:online";
/// Heartbeat lifetime. The app reports every ~5 s, so this survives a few missed reports.
const SEEN_TTL_SECS: u64 = 45;
/// How long a last-known position is shown to a rider after the driver's app goes quiet.
const LOC_TTL_SECS: u64 = 120;
const ACTIVE: &str = "('accepted', 'arriving', 'in_progress')";
/// A GPS fix implying more than this speed since the last one is a glitch or a spoofed position
/// and is dropped (200 km/h; small jumps are allowed for GPS noise).
const MAX_SPEED_MPS: f64 = 55.0;
const SPEED_CHECK_MIN_METERS: f64 = 500.0;

pub(crate) fn seen_key(driver: Uuid) -> String {
    format!("nova:driver:{driver}:seen")
}
fn loc_key(driver: Uuid) -> String {
    format!("nova:driver:{driver}:loc")
}

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/driver/me", get(me))
        .route("/v1/driver/apply", post(apply))
        .route("/v1/driver/submit", post(submit))
        .route("/v1/driver/documents", get(documents))
        .route("/v1/driver/payout", put(set_payout))
        .route("/v1/driver/trips", get(trips))
        .route("/v1/driver/earnings", get(earnings))
        .route("/v1/driver/vehicle", put(set_vehicle))
        .route("/v1/driver/products", put(set_product).get(product_rules))
        .route("/v1/driver/online", post(go_online))
        .route("/v1/driver/offline", post(go_offline))
        .route("/v1/driver/location", post(location))
        .route("/v1/driver/state", get(state))
        .route("/v1/driver/offers/{id}/accept", post(accept))
        .route("/v1/driver/offers/{id}/decline", post(decline))
        .route("/v1/driver/rides/{id}/arrived", post(arrived))
        .route("/v1/driver/rides/{id}/start", post(start))
        .route("/v1/driver/rides/{id}/complete", post(complete))
        .route("/v1/driver/rides/{id}/cancel", post(cancel))
        .route("/v1/driver/rides/{id}/route", get(ride_route))
        .route("/v1/driver/surge", get(surge_map))
        .route("/v1/driver/destination", put(set_destination).delete(clear_destination))
}

// ---------- shapes ----------

#[derive(Serialize, FromRow)]
pub struct DriverProfile {
    status: String,
    licence_number: String,
    licence_expiry: Option<NaiveDate>,
    city: Option<String>,
    country_code: Option<String>,
    has_passenger_licence: bool,
    approved_at: Option<DateTime<Utc>>,
    review_note: Option<String>,
    created_at: DateTime<Utc>,
}

#[derive(Serialize, FromRow)]
pub struct Vehicle {
    id: Uuid,
    make: String,
    model: String,
    color: String,
    plate: String,
    year: i32,
    seats: i32,
    is_electric: bool,
    wheelchair_accessible: bool,
}

#[derive(Serialize, FromRow)]
pub struct DriverProduct {
    product_id: String,
    name: String,
    enabled: bool,
}

#[derive(Serialize)]
pub struct DriverMe {
    user: User,
    /// None until the user applies to drive.
    driver: Option<DriverProfile>,
    vehicle: Option<Vehicle>,
    /// Ride types the car qualifies for, with the driver's on/off choice.
    products: Vec<DriverProduct>,
    online: bool,
    /// Required documents and the latest upload of each.
    documents: Vec<DriverDocument>,
    /// Where earnings are paid (account number masked).
    payout: Option<PayoutSummary>,
    /// Destination filter, while set.
    destination: Option<Destination>,
    destination_uses_left: i64,
}

/// One required document and the driver's latest upload of it (photo in S3, see media.rs).
#[derive(Serialize, FromRow)]
pub struct DriverDocument {
    category: String,
    name: String,
    description: String,
    required: bool,
    media_id: Option<Uuid>,
    /// None (not uploaded), uploaded (in review), verified, rejected.
    status: Option<String>,
    rejection_reason: Option<String>,
    uploaded_at: Option<DateTime<Utc>>,
}

#[derive(Serialize, FromRow)]
pub struct PayoutSummary {
    account_name: String,
    /// Last 4 digits only; the full number never goes back to the phone.
    account_last4: String,
    gst_registered: bool,
    payout_schedule: String,
}

/// Latest non-pending upload per document type (pending = presigned but never uploaded).
pub(crate) const DOCUMENTS_SQL: &str = "SELECT t.category, t.name, t.description, t.required,
            m.id AS media_id, m.status, m.rejection_reason, m.created_at AS uploaded_at
     FROM driver_document_types t
     LEFT JOIN LATERAL (
        SELECT id, status, rejection_reason, created_at FROM media_objects
        WHERE owner_user_id = $1 AND category = t.category AND status <> 'pending'
        ORDER BY created_at DESC LIMIT 1
     ) m ON true
     ORDER BY t.sort_order";

pub(crate) async fn load_documents(st: &AppState, user: Uuid) -> Result<Vec<DriverDocument>, ApiError> {
    Ok(sqlx::query_as::<_, DriverDocument>(DOCUMENTS_SQL).bind(user).fetch_all(&st.pool).await?)
}

async fn payout_summary(st: &AppState, user: Uuid) -> Result<Option<PayoutSummary>, ApiError> {
    Ok(sqlx::query_as::<_, PayoutSummary>(
        "SELECT account_name, right(regexp_replace(account_number, '[^0-9A-Za-z]', '', 'g'), 4) AS account_last4,
                gst_registered, payout_schedule
         FROM driver_payout_accounts WHERE user_id = $1",
    )
    .bind(user)
    .fetch_optional(&st.pool)
    .await?)
}

#[derive(Serialize)]
pub struct Place {
    name: String,
    address: String,
    lat: f64,
    lng: f64,
}

/// A ride offer as the driver sees it while deciding.
#[derive(Serialize)]
pub struct Offer {
    offer_id: Uuid,
    ride_id: Uuid,
    /// Time left to answer, measured on the server so phone clock drift doesn't matter.
    expires_in_ms: i64,
    product_name: String,
    currency: String,
    fare_cents: i64,
    /// What the driver will earn (fare minus booking and service fees), shown upfront as Uber does.
    earnings_cents: i64,
    /// Straight-line distance from the driver to the pickup when offered.
    pickup_distance_meters: i32,
    distance_meters: i64,
    duration_seconds: i64,
    polyline: String,
    pickup: Place,
    dropoff: Place,
    stop_count: i64,
    payment_method: String,
    rider_name: String,
}

#[derive(FromRow)]
struct OfferRow {
    offer_id: Uuid,
    ride_id: Uuid,
    expires_in_ms: i64,
    product_name: String,
    currency: String,
    fare_cents: i64,
    earnings_cents: Option<i64>,
    pickup_distance_meters: i32,
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
    stop_count: i64,
    payment_method: String,
    rider_name: String,
}

impl From<OfferRow> for Offer {
    fn from(r: OfferRow) -> Self {
        Offer {
            offer_id: r.offer_id,
            ride_id: r.ride_id,
            expires_in_ms: r.expires_in_ms.max(0),
            product_name: r.product_name,
            currency: r.currency,
            fare_cents: r.fare_cents,
            earnings_cents: r.earnings_cents.unwrap_or(r.fare_cents),
            pickup_distance_meters: r.pickup_distance_meters,
            distance_meters: r.distance_meters,
            duration_seconds: r.duration_seconds,
            polyline: r.polyline,
            pickup: Place { name: r.pickup_name, address: r.pickup_address, lat: r.pickup_lat, lng: r.pickup_lng },
            dropoff: Place { name: r.dropoff_name, address: r.dropoff_address, lat: r.dropoff_lat, lng: r.dropoff_lng },
            stop_count: r.stop_count,
            payment_method: r.payment_method,
            rider_name: r.rider_name,
        }
    }
}

/// The ride a driver is working (or just finished).
#[derive(Serialize)]
pub struct DriverRide {
    id: Uuid,
    status: String,
    product_name: String,
    currency: String,
    fare_cents: i64,
    distance_meters: i64,
    duration_seconds: i64,
    polyline: String,
    pickup: Place,
    dropoff: Place,
    stops: Vec<RideStop>,
    payment_method: String,
    /// Who to pick up: the passenger when booked for someone else, else the rider.
    rider_name: String,
    /// Contact number, only while the ride is active.
    rider_phone: Option<String>,
    /// Set when someone booked this ride for the person riding.
    booked_by: Option<String>,
    accepted_at: Option<DateTime<Utc>>,
    arrived_at: Option<DateTime<Utc>>,
    started_at: Option<DateTime<Utc>>,
    completed_at: Option<DateTime<Utc>>,
    /// Seconds at the pickup before the driver may cancel as a no-show (from the ride's card).
    no_show_wait_secs: i32,
    /// Seconds of free waiting before wait-time charges start.
    wait_grace_secs: i32,
    /// What the driver earned, once the trip is settled.
    driver_earnings_cents: Option<i64>,
    /// "upfront", or "actual" when the trip changed and was repriced.
    fare_basis: String,
}

#[derive(FromRow)]
struct DriverRideRow {
    id: Uuid,
    status: String,
    product_name: String,
    currency: String,
    fare_cents: i64,
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
    rider_name: String,
    rider_phone: Option<String>,
    booked_by: Option<String>,
    accepted_at: Option<DateTime<Utc>>,
    arrived_at: Option<DateTime<Utc>>,
    started_at: Option<DateTime<Utc>>,
    completed_at: Option<DateTime<Utc>>,
    no_show_wait_secs: Option<i32>,
    wait_grace_secs: Option<i32>,
    driver_earnings_cents: Option<i64>,
    fare_basis: String,
}

#[derive(Serialize)]
pub struct DriverState {
    online: bool,
    /// The offer waiting for an answer, if any.
    offer: Option<Offer>,
    /// The ride being worked, if any.
    ride: Option<DriverRide>,
}

#[derive(Deserialize, Clone, Copy)]
pub struct LatLng {
    pub(crate) lat: f64,
    pub(crate) lng: f64,
    /// Direction of travel in degrees from north, when the phone knows it.
    #[serde(default)]
    pub(crate) heading: Option<f64>,
    /// Speed in m/s, when the phone knows it.
    #[serde(default)]
    pub(crate) speed: Option<f64>,
}

impl LatLng {
    fn check(&self) -> Result<(), ApiError> {
        let ok = self.lat.is_finite() && self.lng.is_finite() && self.lat.abs() <= 85.0 && self.lng.abs() <= 180.0;
        if ok { Ok(()) } else { Err(ApiError::BadRequest("invalid_location")) }
    }
}

// ---------- reads ----------

async fn driver_profile(st: &AppState, user: Uuid) -> Result<Option<DriverProfile>, ApiError> {
    Ok(sqlx::query_as::<_, DriverProfile>(
        "SELECT status, licence_number, licence_expiry, city, country_code, has_passenger_licence, approved_at, review_note, created_at FROM drivers WHERE user_id = $1",
    )
    .bind(user)
    .fetch_optional(&st.pool)
    .await?)
}

async fn active_vehicle(st: &AppState, user: Uuid) -> Result<Option<Vehicle>, ApiError> {
    Ok(sqlx::query_as::<_, Vehicle>(
        "SELECT id, make, model, color, plate, year, seats, is_electric, wheelchair_accessible
         FROM vehicles WHERE driver_id = $1 AND active",
    )
    .bind(user)
    .fetch_optional(&st.pool)
    .await?)
}

async fn products(st: &AppState, user: Uuid) -> Result<Vec<DriverProduct>, ApiError> {
    Ok(sqlx::query_as::<_, DriverProduct>(
        "SELECT dp.product_id, p.name, dp.enabled FROM driver_products dp
         JOIN ride_products p ON p.id = dp.product_id
         WHERE dp.driver_id = $1 ORDER BY p.sort_order",
    )
    .bind(user)
    .fetch_all(&st.pool)
    .await?)
}

async fn is_online(st: &AppState, user: Uuid) -> Result<bool, ApiError> {
    let mut redis = st.redis.clone();
    let alive: bool = redis::cmd("EXISTS").arg(seen_key(user)).query_async(&mut redis).await?;
    Ok(alive)
}

/// One account, one role: only accounts with `users.role = 'driver'` use the driver API. A rider
/// or admin account signing in to the driver app is refused, so it can't also become a driver.
async fn require_driver(st: &AppState, headers: &HeaderMap) -> Result<Uuid, ApiError> {
    let user = require_user_id(st, headers)?;
    let role: Option<String> = sqlx::query_scalar("SELECT role FROM users WHERE id = $1")
        .bind(user)
        .fetch_optional(&st.pool)
        .await?;
    match role.as_deref() {
        Some("driver") => Ok(user),
        Some(_) => Err(ApiError::Forbidden("not_driver_account")),
        None => Err(ApiError::Unauthorized),
    }
}

async fn me(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<DriverMe>, ApiError> {
    let user = require_driver(&st, &headers).await?;
    Ok(Json(load_me(&st, user).await?))
}

async fn load_me(st: &AppState, user: Uuid) -> Result<DriverMe, ApiError> {
    Ok(DriverMe {
        user: load_user(st, user).await?,
        driver: driver_profile(st, user).await?,
        vehicle: active_vehicle(st, user).await?,
        products: products(st, user).await?,
        online: is_online(st, user).await?,
        documents: load_documents(st, user).await?,
        payout: payout_summary(st, user).await?,
        destination: destination(st, user).await?,
        destination_uses_left: destination_uses_left(st, user).await?,
    })
}

async fn open_offer(st: &AppState, driver: Uuid) -> Result<Option<Offer>, ApiError> {
    let row = sqlx::query_as::<_, OfferRow>(
        "SELECT o.id AS offer_id, o.ride_id,
                (extract(epoch FROM o.expires_at - now()) * 1000)::bigint AS expires_in_ms,
                p.name AS product_name, r.currency, r.fare_cents, o.distance_meters AS pickup_distance_meters,
                (SELECT round(greatest(r.fare_cents - c.booking_fee_cents, 0) * (10000 - c.service_fee_bps) / 10000.0)::bigint
                 FROM ride_rate_cards c WHERE c.ride_id = r.id) AS earnings_cents,
                r.distance_meters, r.duration_seconds, r.polyline,
                r.pickup_name, r.pickup_address, r.pickup_lat, r.pickup_lng,
                r.dropoff_name, r.dropoff_address, r.dropoff_lat, r.dropoff_lng,
                (SELECT count(*) FROM ride_stops s WHERE s.ride_id = r.id) AS stop_count,
                r.payment_method, COALESCE(r.passenger_name, u.first_name) AS rider_name
         FROM ride_offers o
         JOIN rides r ON r.id = o.ride_id
         JOIN ride_products p ON p.id = r.product_id
         JOIN users u ON u.id = r.rider_id
         WHERE o.driver_id = $1 AND o.response IS NULL AND o.expires_at > now() AND r.status = 'requested'
         ORDER BY o.offered_at DESC LIMIT 1",
    )
    .bind(driver)
    .fetch_optional(&st.pool)
    .await?;
    Ok(row.map(Offer::from))
}

/// One offer as pushed over the live socket (same shape as in `GET /v1/driver/state`).
pub(crate) async fn offer_event(st: &AppState, driver: Uuid) -> Option<serde_json::Value> {
    match open_offer(st, driver).await {
        Ok(Some(offer)) => Some(json!({ "type": "offer", "offer": offer })),
        _ => None,
    }
}

/// The driver's active ride, or a specific ride of theirs when `id` is given (any status).
async fn driver_ride(st: &AppState, driver: Uuid, id: Option<Uuid>) -> Result<Option<DriverRide>, ApiError> {
    let row = sqlx::query_as::<_, DriverRideRow>(&format!(
        "SELECT r.id, r.status, p.name AS product_name, r.currency, r.fare_cents, r.distance_meters,
                r.duration_seconds, r.polyline,
                r.pickup_name, r.pickup_address, r.pickup_lat, r.pickup_lng,
                r.dropoff_name, r.dropoff_address, r.dropoff_lat, r.dropoff_lng, r.payment_method,
                COALESCE(r.passenger_name, u.first_name) AS rider_name,
                CASE WHEN r.status IN {ACTIVE} THEN
                    CASE WHEN r.passenger_phone IS NOT NULL THEN r.passenger_phone
                         WHEN u.phone_verified_at IS NOT NULL THEN u.phone_e164 END
                END AS rider_phone,
                CASE WHEN r.passenger_name IS NOT NULL THEN u.first_name END AS booked_by,
                r.accepted_at, r.arrived_at, r.started_at, r.completed_at,
                COALESCE(c.no_show_wait_secs, f.no_show_wait_secs) AS no_show_wait_secs,
                COALESCE(c.wait_grace_secs, f.wait_grace_secs) AS wait_grace_secs,
                r.driver_earnings_cents, r.fare_basis
         FROM rides r
         JOIN ride_products p ON p.id = r.product_id
         JOIN users u ON u.id = r.rider_id
         LEFT JOIN ride_rate_cards c ON c.ride_id = r.id
         LEFT JOIN fare_rates f ON f.service_area_id = r.service_area_id AND f.product_id = r.product_id
         WHERE r.driver_id = $1 AND (($2::uuid IS NULL AND r.status IN {ACTIVE}) OR r.id = $2)
         LIMIT 1"
    ))
    .bind(driver)
    .bind(id)
    .fetch_optional(&st.pool)
    .await?;
    let Some(r) = row else { return Ok(None) };
    let stops = sqlx::query_as::<_, (String, f64, f64)>(
        "SELECT name, lat, lng FROM ride_stops WHERE ride_id = $1 ORDER BY stop_order",
    )
    .bind(r.id)
    .fetch_all(&st.pool)
    .await?
    .into_iter()
    .map(|(name, lat, lng)| RideStop { name, lat, lng })
    .collect();
    Ok(Some(DriverRide {
        id: r.id,
        status: r.status,
        product_name: r.product_name,
        currency: r.currency,
        fare_cents: r.fare_cents,
        distance_meters: r.distance_meters,
        duration_seconds: r.duration_seconds,
        polyline: r.polyline,
        pickup: Place { name: r.pickup_name, address: r.pickup_address, lat: r.pickup_lat, lng: r.pickup_lng },
        dropoff: Place { name: r.dropoff_name, address: r.dropoff_address, lat: r.dropoff_lat, lng: r.dropoff_lng },
        stops,
        payment_method: r.payment_method,
        rider_name: r.rider_name,
        rider_phone: r.rider_phone,
        booked_by: r.booked_by,
        accepted_at: r.accepted_at,
        arrived_at: r.arrived_at,
        started_at: r.started_at,
        completed_at: r.completed_at,
        no_show_wait_secs: r.no_show_wait_secs.unwrap_or(300),
        wait_grace_secs: r.wait_grace_secs.unwrap_or(120),
        driver_earnings_cents: r.driver_earnings_cents,
        fare_basis: r.fare_basis,
    }))
}

/// What the driver app shows: online or not, an offer to answer, the ride in progress.
/// The app reads this on start, on every socket (re)connect, and as a slow fallback poll.
async fn state(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<DriverState>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    Ok(Json(load_state(&st, driver).await?))
}

async fn load_state(st: &AppState, driver: Uuid) -> Result<DriverState, ApiError> {
    Ok(DriverState {
        online: is_online(st, driver).await?,
        offer: open_offer(st, driver).await?,
        ride: driver_ride(st, driver, None).await?,
    })
}

/// Last reported position of a driver, for the rider's map.
pub(crate) async fn last_location(st: &AppState, driver: Uuid) -> Option<(f64, f64)> {
    let mut redis = st.redis.clone();
    let raw: Option<String> = redis::cmd("GET").arg(loc_key(driver)).query_async(&mut redis).await.ok()?;
    let raw = raw?;
    let mut parts = raw.split(',');
    let lat = parts.next()?.parse().ok()?;
    let lng = parts.next()?.parse().ok()?;
    Some((lat, lng))
}

/// Last fix with its time in unix milliseconds (older values have no time).
async fn last_fix(st: &AppState, driver: Uuid) -> Option<(f64, f64, Option<i64>)> {
    let mut redis = st.redis.clone();
    let raw: Option<String> = redis::cmd("GET").arg(loc_key(driver)).query_async(&mut redis).await.ok()?;
    let raw = raw?;
    let mut parts = raw.split(',');
    let lat = parts.next()?.parse().ok()?;
    let lng = parts.next()?.parse().ok()?;
    Some((lat, lng, parts.next().and_then(|t| t.parse().ok())))
}

/// Great-circle distance in metres (for the speed check only; trip distances use PostGIS).
fn haversine_m(a: (f64, f64), b: (f64, f64)) -> f64 {
    let (la1, lo1, la2, lo2) = (a.0.to_radians(), a.1.to_radians(), b.0.to_radians(), b.1.to_radians());
    let h = ((la2 - la1) / 2.0).sin().powi(2) + la1.cos() * la2.cos() * ((lo2 - lo1) / 2.0).sin().powi(2);
    2.0 * 6_371_000.0 * h.sqrt().asin()
}

/// Free drivers among `ids`: app still reporting and not on a ride. Supply for surge.
pub(crate) async fn count_free(st: &AppState, ids: &[String]) -> Result<i64, ApiError> {
    if ids.is_empty() {
        return Ok(0);
    }
    let mut redis = st.redis.clone();
    let mut check = redis::pipe();
    for id in ids {
        check.cmd("EXISTS").arg(format!("nova:driver:{id}:seen"));
    }
    let alive: Vec<bool> = check.query_async(&mut redis).await?;
    let alive: Vec<Uuid> = ids.iter().zip(alive).filter(|(_, ok)| *ok).filter_map(|(id, _)| Uuid::parse_str(id).ok()).collect();
    if alive.is_empty() {
        return Ok(0);
    }
    Ok(sqlx::query_scalar(&format!(
        "SELECT count(*) FROM unnest($1::uuid[]) AS d(id)
         WHERE NOT EXISTS (SELECT 1 FROM rides r WHERE r.driver_id = d.id AND r.status IN {ACTIVE})"
    ))
    .bind(&alive)
    .fetch_one(&st.pool)
    .await?)
}

/// The driver's current position, or `location_required` when the app hasn't reported one in
/// the last two minutes. Arrived, no-show and drop-off are checked against it.
async fn current_position(st: &AppState, driver: Uuid) -> Result<(f64, f64), ApiError> {
    last_location(st, driver).await.ok_or(ApiError::Conflict("location_required"))
}

// ---------- applying and the car ----------

#[derive(Deserialize)]
pub struct ApplyReq {
    licence_number: String,
    licence_expiry: Option<NaiveDate>,
    city: Option<String>,
    country_code: Option<String>,
    has_passenger_licence: Option<bool>,
}

/// Starts (or updates, while still pending) a driver application.
async fn apply(State(st): State<AppState>, headers: HeaderMap, Json(req): Json<ApplyReq>) -> Result<Json<DriverMe>, ApiError> {
    let user = require_driver(&st, &headers).await?;
    rate_limit(&st, "driver_apply", user, 10, 3600).await?;
    // Riders and support need to reach a driver: a verified phone is required.
    if verified_phone(&st, user).await.is_none() {
        return Err(ApiError::BadRequest("phone_required"));
    }
    let licence: String = req.licence_number.trim().to_uppercase();
    if !(4..=20).contains(&licence.len()) || !licence.chars().all(|c| c.is_ascii_alphanumeric() || c == '-') {
        return Err(ApiError::BadRequest("invalid_licence"));
    }
    if req.licence_expiry.is_some_and(|d| d < Utc::now().date_naive()) {
        return Err(ApiError::BadRequest("licence_expired"));
    }
    let city = req.city.as_deref().map(str::trim).filter(|s| !s.is_empty()).map(|s| s.chars().take(100).collect::<String>());
    let country_code = req
        .country_code
        .as_deref()
        .map(str::trim)
        .filter(|c| c.len() == 2 && c.chars().all(|ch| ch.is_ascii_alphabetic()))
        .map(|c| c.to_ascii_uppercase());
    let has_passenger_licence = req.has_passenger_licence.unwrap_or(false);
    sqlx::query(
        "INSERT INTO drivers (user_id, licence_number, licence_expiry, city, country_code, has_passenger_licence, status)
         VALUES ($1, $2, $3, $4, $5, $6, 'pending')
         ON CONFLICT (user_id) DO UPDATE SET
            licence_number = EXCLUDED.licence_number,
            licence_expiry = EXCLUDED.licence_expiry,
            city = COALESCE(EXCLUDED.city, drivers.city),
            country_code = COALESCE(EXCLUDED.country_code, drivers.country_code),
            has_passenger_licence = EXCLUDED.has_passenger_licence,
            updated_at = now()
         WHERE drivers.status = 'pending'",
    )
    .bind(user)
    .bind(&licence)
    .bind(req.licence_expiry)
    .bind(city)
    .bind(country_code)
    .bind(has_passenger_licence)
    .execute(&st.pool)
    .await?;
    tracing::info!(%user, "driver application saved");
    Ok(Json(load_me(&st, user).await?))
}

/// Sends the application for review once everything is in: car, every required document
/// uploaded, payout details. `DRIVER_AUTO_APPROVE=true` (development) approves on the spot.
async fn submit(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<DriverMe>, ApiError> {
    let user = require_driver(&st, &headers).await?;
    rate_limit(&st, "driver_submit", user, 20, 3600).await?;
    let me = load_me(&st, user).await?;
    let Some(driver) = &me.driver else { return Err(ApiError::Conflict("not_a_driver")) };
    if me.vehicle.is_none() {
        return Err(ApiError::Conflict("vehicle_required"));
    }
    let missing = me.documents.iter().any(|d| d.required && !matches!(d.status.as_deref(), Some("uploaded" | "verified")));
    if missing {
        return Err(ApiError::Conflict("documents_missing"));
    }
    if me.payout.is_none() {
        return Err(ApiError::Conflict("payout_required"));
    }
    if driver.status == "pending" {
        let auto = st.cfg.driver_auto_approve;
        sqlx::query(
            "UPDATE drivers SET submitted_at = now(), updated_at = now(),
                    status = CASE WHEN $2 THEN 'approved' ELSE status END,
                    approved_at = CASE WHEN $2 THEN now() ELSE approved_at END
             WHERE user_id = $1 AND status = 'pending'",
        )
        .bind(user)
        .bind(auto)
        .execute(&st.pool)
        .await?;
        if auto {
            sqlx::query(
                "UPDATE media_objects SET status = 'verified', reviewed_at = now(), updated_at = now()
                 WHERE owner_user_id = $1 AND status = 'uploaded'
                   AND category IN (SELECT category FROM driver_document_types)",
            )
            .bind(user)
            .execute(&st.pool)
            .await?;
        }
        tracing::info!(%user, auto_approved = auto, "driver application submitted");
    }
    Ok(Json(load_me(&st, user).await?))
}

async fn documents(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<Vec<DriverDocument>>, ApiError> {
    let user = require_driver(&st, &headers).await?;
    Ok(Json(load_documents(&st, user).await?))
}

#[derive(Deserialize)]
pub struct PayoutReq {
    account_name: String,
    account_number: String,
    #[serde(default)]
    tax_number: String,
    #[serde(default)]
    gst_registered: bool,
    payout_schedule: String,
}

/// Saves where earnings are paid. Bank formats differ by country, so this checks shape only
/// (letters, digits, spaces and dashes; 6-34 characters covers local numbers and IBANs).
async fn set_payout(State(st): State<AppState>, headers: HeaderMap, Json(req): Json<PayoutReq>) -> Result<Json<DriverMe>, ApiError> {
    let user = require_driver(&st, &headers).await?;
    rate_limit(&st, "driver_payout", user, 20, 3600).await?;
    let name: String = req.account_name.split_whitespace().collect::<Vec<_>>().join(" ");
    if name.is_empty() || name.chars().count() > 80 {
        return Err(ApiError::BadRequest("invalid_account_name"));
    }
    let number = req.account_number.trim().to_uppercase();
    let compact: String = number.chars().filter(|c| c.is_ascii_alphanumeric()).collect();
    if !(6..=34).contains(&compact.len()) || !number.chars().all(|c| c.is_ascii_alphanumeric() || c == ' ' || c == '-') {
        return Err(ApiError::BadRequest("invalid_account_number"));
    }
    let tax = req.tax_number.trim().to_string();
    if tax.len() > 20 || !tax.chars().all(|c| c.is_ascii_alphanumeric() || c == ' ' || c == '-') {
        return Err(ApiError::BadRequest("invalid_tax_number"));
    }
    if !matches!(req.payout_schedule.as_str(), "daily" | "weekly" | "instant") {
        return Err(ApiError::BadRequest("invalid_payout_schedule"));
    }
    sqlx::query(
        "INSERT INTO driver_payout_accounts (user_id, account_name, account_number, tax_number, gst_registered, payout_schedule)
         VALUES ($1, $2, $3, $4, $5, $6)
         ON CONFLICT (user_id) DO UPDATE SET
            account_name = EXCLUDED.account_name, account_number = EXCLUDED.account_number,
            tax_number = EXCLUDED.tax_number, gst_registered = EXCLUDED.gst_registered,
            payout_schedule = EXCLUDED.payout_schedule, updated_at = now()",
    )
    .bind(user)
    .bind(&name)
    .bind(&number)
    .bind(&tax)
    .bind(req.gst_registered)
    .bind(&req.payout_schedule)
    .execute(&st.pool)
    .await?;
    Ok(Json(load_me(&st, user).await?))
}

// ---------- trips and earnings ----------

#[derive(Serialize, FromRow)]
pub struct DriverTrip {
    id: Uuid,
    status: String,
    product_name: String,
    currency: String,
    fare_cents: i64,
    /// The driver's share (fare minus booking and service fees, plus long pickup pay), or a
    /// cancellation fee's share. NULL for trips settled before earnings were recorded.
    driver_earnings_cents: Option<i64>,
    distance_meters: i64,
    pickup_name: String,
    dropoff_name: String,
    rider_name: String,
    cancel_reason: Option<String>,
    accepted_at: Option<DateTime<Utc>>,
    started_at: Option<DateTime<Utc>>,
    ended_at: Option<DateTime<Utc>>,
}

#[derive(Deserialize)]
pub struct RangeQuery {
    /// Inclusive start, exclusive end (the app sends local midnights as UTC instants).
    from: Option<DateTime<Utc>>,
    to: Option<DateTime<Utc>>,
    limit: Option<i64>,
}

const TRIP_SELECT: &str = "SELECT r.id, r.status, p.name AS product_name, r.currency, r.fare_cents, r.driver_earnings_cents, r.distance_meters,
        r.pickup_name, r.dropoff_name, COALESCE(r.passenger_name, u.first_name) AS rider_name,
        r.cancel_reason, r.accepted_at, r.started_at, COALESCE(r.ended_at, r.completed_at) AS ended_at
     FROM rides r JOIN ride_products p ON p.id = r.product_id JOIN users u ON u.id = r.rider_id";

/// Finished trips (completed or cancelled after accepting), newest first.
async fn trips(State(st): State<AppState>, headers: HeaderMap, axum::extract::Query(q): axum::extract::Query<RangeQuery>) -> Result<Json<Vec<DriverTrip>>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    let rows = sqlx::query_as::<_, DriverTrip>(&format!(
        "{TRIP_SELECT}
         WHERE r.driver_id = $1 AND r.status IN ('completed', 'cancelled')
           AND ($2::timestamptz IS NULL OR COALESCE(r.ended_at, r.completed_at) >= $2)
           AND ($3::timestamptz IS NULL OR COALESCE(r.ended_at, r.completed_at) < $3)
         ORDER BY COALESCE(r.ended_at, r.completed_at) DESC NULLS LAST LIMIT $4"
    ))
    .bind(driver)
    .bind(q.from)
    .bind(q.to)
    .bind(q.limit.unwrap_or(50).clamp(1, 500))
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(rows))
}

#[derive(Serialize)]
pub struct Earnings {
    /// Currency of the driver's most recent trip in the range (one driver works one area).
    currency: Option<String>,
    total_cents: i64,
    trips: i64,
    online_seconds: i64,
    /// Completed trips in the range, for the app to chart by hour/day/week in local time.
    rides: Vec<EarningPoint>,
}

#[derive(Serialize, FromRow)]
pub struct EarningPoint {
    completed_at: DateTime<Utc>,
    fare_cents: i64,
    currency: String,
}

/// Fares of completed trips in [from, to) plus hours online. No commission model exists yet,
/// so this is the fare total; a service fee becomes one more column here when it does.
async fn earnings(State(st): State<AppState>, headers: HeaderMap, axum::extract::Query(q): axum::extract::Query<RangeQuery>) -> Result<Json<Earnings>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    let from = q.from.unwrap_or_else(|| Utc::now() - chrono::Duration::days(1));
    let to = q.to.unwrap_or_else(Utc::now);
    if to <= from || to - from > chrono::Duration::days(62) {
        return Err(ApiError::BadRequest("invalid_range"));
    }
    let rides = sqlx::query_as::<_, EarningPoint>(
        "SELECT completed_at, COALESCE(driver_earnings_cents, fare_cents) AS fare_cents, currency FROM rides
         WHERE driver_id = $1 AND status = 'completed' AND completed_at >= $2 AND completed_at < $3
         ORDER BY completed_at",
    )
    .bind(driver)
    .bind(from)
    .bind(to)
    .fetch_all(&st.pool)
    .await?;
    let currency = rides.last().map(|r| r.currency.clone());
    let total_cents = rides.iter().filter(|r| Some(&r.currency) == currency.as_ref()).map(|r| r.fare_cents).sum();
    // Overlap of each shift with the range; open shifts count up to their last heartbeat.
    let online_seconds: i64 = sqlx::query_scalar(
        "SELECT COALESCE(sum(extract(epoch FROM least(COALESCE(ended_at, last_seen_at), $3) - greatest(started_at, $2))), 0)::bigint
         FROM driver_shifts
         WHERE driver_id = $1 AND started_at < $3 AND COALESCE(ended_at, last_seen_at) > $2",
    )
    .bind(driver)
    .bind(from)
    .bind(to)
    .fetch_one(&st.pool)
    .await?;
    Ok(Json(Earnings { currency, total_cents, trips: rides.len() as i64, online_seconds, rides }))
}

// ---------- shifts (hours online) ----------

async fn shift_started(st: &AppState, driver: Uuid) {
    // Close a shift whose app went quiet, then open one unless a live one exists.
    let res = sqlx::query(
        "WITH stale AS (
            UPDATE driver_shifts SET ended_at = last_seen_at
            WHERE driver_id = $1 AND ended_at IS NULL AND last_seen_at < now() - interval '2 minutes'
         )
         INSERT INTO driver_shifts (id, driver_id) SELECT $2, $1
         WHERE NOT EXISTS (SELECT 1 FROM driver_shifts WHERE driver_id = $1 AND ended_at IS NULL AND last_seen_at >= now() - interval '2 minutes')
         ON CONFLICT DO NOTHING",
    )
    .bind(driver)
    .bind(Uuid::now_v7())
    .execute(&st.pool)
    .await;
    if let Err(err) = res {
        tracing::warn!(error = ?err, %driver, "could not start shift");
    }
}

async fn shift_seen(st: &AppState, driver: Uuid) {
    // At most one write every 30 s per driver, not one per GPS fix.
    let _ = sqlx::query(
        "UPDATE driver_shifts SET last_seen_at = now()
         WHERE driver_id = $1 AND ended_at IS NULL AND last_seen_at < now() - interval '30 seconds'",
    )
    .bind(driver)
    .execute(&st.pool)
    .await;
}

async fn shift_ended(st: &AppState, driver: Uuid) {
    let _ = sqlx::query("UPDATE driver_shifts SET ended_at = now(), last_seen_at = now() WHERE driver_id = $1 AND ended_at IS NULL")
        .bind(driver)
        .execute(&st.pool)
        .await;
}

#[derive(Deserialize)]
pub struct VehicleReq {
    make: String,
    model: String,
    color: String,
    plate: String,
    year: i32,
    seats: i32,
    #[serde(default)]
    is_electric: bool,
    #[serde(default)]
    wheelchair_accessible: bool,
}

fn text(value: &str, max: usize) -> Result<String, ApiError> {
    let v: String = value.split_whitespace().collect::<Vec<_>>().join(" ");
    if v.is_empty() || v.chars().count() > max {
        return Err(ApiError::BadRequest("invalid_vehicle"));
    }
    Ok(v)
}

/// Sets the driver's car. A change retires the old row (rides keep their car) and recomputes
/// which ride types the driver can take.
async fn set_vehicle(State(st): State<AppState>, headers: HeaderMap, Json(req): Json<VehicleReq>) -> Result<Json<DriverMe>, ApiError> {
    let user = require_driver(&st, &headers).await?;
    rate_limit(&st, "driver_vehicle", user, 20, 3600).await?;
    let make = text(&req.make, 40)?;
    let model = text(&req.model, 40)?;
    let color = text(&req.color, 20)?;
    let plate: String = req.plate.trim().to_uppercase();
    if !(2..=10).contains(&plate.len()) || !plate.chars().all(|c| c.is_ascii_alphanumeric() || c == ' ' || c == '-') {
        return Err(ApiError::BadRequest("invalid_plate"));
    }
    let this_year = Utc::now().year();
    if !(1990..=this_year + 1).contains(&req.year) {
        return Err(ApiError::BadRequest("invalid_year"));
    }
    if !(1..=8).contains(&req.seats) {
        return Err(ApiError::BadRequest("invalid_seats"));
    }

    let mut tx = st.pool.begin().await?;
    let status: Option<String> = sqlx::query_scalar("SELECT status FROM drivers WHERE user_id = $1 FOR UPDATE")
        .bind(user)
        .fetch_optional(&mut *tx)
        .await?;
    if status.is_none() {
        return Err(ApiError::Conflict("not_a_driver"));
    }
    let busy: bool = sqlx::query_scalar(&format!(
        "SELECT EXISTS (SELECT 1 FROM rides WHERE driver_id = $1 AND status IN {ACTIVE})"
    ))
    .bind(user)
    .fetch_one(&mut *tx)
    .await?;
    if busy {
        return Err(ApiError::Conflict("ride_in_progress"));
    }
    sqlx::query("UPDATE vehicles SET active = false, retired_at = now() WHERE driver_id = $1 AND active")
        .bind(user)
        .execute(&mut *tx)
        .await?;
    let inserted = sqlx::query(
        "INSERT INTO vehicles (id, driver_id, make, model, color, plate, year, seats, is_electric, wheelchair_accessible)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)",
    )
    .bind(Uuid::now_v7())
    .bind(user)
    .bind(&make)
    .bind(&model)
    .bind(&color)
    .bind(&plate)
    .bind(req.year)
    .bind(req.seats)
    .bind(req.is_electric)
    .bind(req.wheelchair_accessible)
    .execute(&mut *tx)
    .await;
    if let Err(err) = inserted {
        return Err(if is_unique_violation(&err) { ApiError::Conflict("plate_in_use") } else { err.into() });
    }
    // Ride types: seats, electric and age rules come from ride_products (data, not code).
    let eligible = "p.seats <= $2 AND (NOT p.requires_electric OR $3)
                    AND (p.max_vehicle_age_years IS NULL OR $4 >= extract(year FROM now())::int - p.max_vehicle_age_years)";
    sqlx::query(&format!(
        "DELETE FROM driver_products dp USING ride_products p
         WHERE dp.product_id = p.id AND dp.driver_id = $1 AND NOT ({eligible})"
    ))
    .bind(user)
    .bind(req.seats)
    .bind(req.is_electric)
    .bind(req.year)
    .execute(&mut *tx)
    .await?;
    sqlx::query(&format!(
        "INSERT INTO driver_products (driver_id, product_id)
         SELECT $1, p.id FROM ride_products p WHERE {eligible}
         ON CONFLICT DO NOTHING"
    ))
    .bind(user)
    .bind(req.seats)
    .bind(req.is_electric)
    .bind(req.year)
    .execute(&mut *tx)
    .await?;
    tx.commit().await?;
    Ok(Json(load_me(&st, user).await?))
}

#[derive(Deserialize)]
pub struct ProductReq {
    product_id: String,
    enabled: bool,
}

/// Turns one ride type on or off (only types the car qualifies for exist here).
async fn set_product(State(st): State<AppState>, headers: HeaderMap, Json(req): Json<ProductReq>) -> Result<Json<DriverMe>, ApiError> {
    let user = require_driver(&st, &headers).await?;
    let done = sqlx::query("UPDATE driver_products SET enabled = $3 WHERE driver_id = $1 AND product_id = $2")
        .bind(user)
        .bind(&req.product_id)
        .bind(req.enabled)
        .execute(&st.pool)
        .await?;
    if done.rows_affected() == 0 {
        return Err(ApiError::BadRequest("invalid_product"));
    }
    Ok(Json(load_me(&st, user).await?))
}

#[derive(Serialize, FromRow)]
pub struct ProductRule {
    id: String,
    name: String,
    seats: i32,
    requires_electric: bool,
    max_vehicle_age_years: Option<i32>,
}

/// What a car needs for each ride type, so the app can preview eligibility while typing.
async fn product_rules(State(st): State<AppState>) -> Result<Json<Vec<ProductRule>>, ApiError> {
    Ok(Json(sqlx::query_as::<_, ProductRule>(
        "SELECT id, name, seats, requires_electric, max_vehicle_age_years FROM ride_products ORDER BY sort_order",
    )
    .fetch_all(&st.pool)
    .await?))
}

// ---------- presence ----------

async fn record_position(st: &AppState, driver: Uuid, at: &LatLng, available: bool) -> Result<(), ApiError> {
    let mut redis = st.redis.clone();
    let mut pipe = redis::pipe();
    let now_ms = Utc::now().timestamp_millis();
    pipe.cmd("SET").arg(loc_key(driver)).arg(format!("{},{},{now_ms}", at.lat, at.lng)).arg("EX").arg(LOC_TTL_SECS).ignore();
    if available {
        pipe.cmd("GEOADD").arg(ONLINE_KEY).arg(at.lng).arg(at.lat).arg(driver.to_string()).ignore();
        pipe.cmd("SET").arg(seen_key(driver)).arg("1").arg("EX").arg(SEEN_TTL_SECS).ignore();
    }
    let _: () = pipe.query_async(&mut redis).await?;
    Ok(())
}

async fn go_online(State(st): State<AppState>, headers: HeaderMap, Json(at): Json<LatLng>) -> Result<Json<DriverState>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    at.check()?;
    rate_limit(&st, "driver_online", driver, 60, 3600).await?;
    match driver_profile(&st, driver).await? {
        None => return Err(ApiError::Conflict("not_a_driver")),
        Some(p) if p.status == "pending" => return Err(ApiError::Conflict("driver_pending")),
        Some(p) if p.status == "suspended" => return Err(ApiError::Conflict("driver_suspended")),
        Some(_) => {}
    }
    if active_vehicle(&st, driver).await?.is_none() {
        return Err(ApiError::Conflict("vehicle_required"));
    }
    if !products(&st, driver).await?.iter().any(|p| p.enabled) {
        return Err(ApiError::Conflict("no_ride_types"));
    }
    record_position(&st, driver, &at, true).await?;
    shift_started(&st, driver).await;
    tracing::info!(%driver, "driver online");
    // Rides already waiting nearby get a chance at this driver right away.
    dispatch::wake_all(&st);
    Ok(Json(load_state(&st, driver).await?))
}

async fn go_offline(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<DriverState>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    let mut redis = st.redis.clone();
    let _: () = redis::pipe()
        .cmd("ZREM").arg(ONLINE_KEY).arg(driver.to_string()).ignore()
        .cmd("DEL").arg(seen_key(driver)).ignore()
        .query_async(&mut redis)
        .await?;
    // An offer they can no longer see is declined now, so the rider isn't left waiting on it.
    let declined: Vec<Uuid> = sqlx::query_scalar(
        "UPDATE ride_offers SET response = 'declined', responded_at = now()
         WHERE driver_id = $1 AND response IS NULL RETURNING ride_id",
    )
    .bind(driver)
    .fetch_all(&st.pool)
    .await?;
    for ride in declined {
        st.dispatch.wake(ride);
    }
    shift_ended(&st, driver).await;
    tracing::info!(%driver, "driver offline");
    Ok(Json(load_state(&st, driver).await?))
}

/// Heartbeat + position. Keeps an online driver in the search set; during a ride it feeds the
/// rider's map even if the driver went offline for new offers.
/// HTTP fallback for location; the live socket carries the same pings (see hub.rs).
async fn location(State(st): State<AppState>, headers: HeaderMap, Json(at): Json<LatLng>) -> Result<Json<serde_json::Value>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    let online = ingest_location(&st, driver, at).await?;
    Ok(Json(json!({ "online": online })))
}

/// One GPS fix from a driver's app, over the socket or HTTP. Rejects impossible jumps, keeps
/// the latest position for dispatch, adds it to the active ride's trail, and pushes it to the
/// rider's map. Returns whether the driver is online.
pub(crate) async fn ingest_location(st: &AppState, driver: Uuid, at: LatLng) -> Result<bool, ApiError> {
    at.check()?;
    rate_limit(st, "driver_location", driver, 120, 60).await?;
    if let Some((lat, lng, Some(at_ms))) = last_fix(st, driver).await {
        let metres = haversine_m((lat, lng), (at.lat, at.lng));
        let secs = ((Utc::now().timestamp_millis() - at_ms) as f64 / 1000.0).max(1.0);
        if metres > SPEED_CHECK_MIN_METERS && metres / secs > MAX_SPEED_MPS {
            tracing::warn!(%driver, metres, secs, "dropped implausible location");
            return Err(ApiError::BadRequest("implausible_location"));
        }
    }
    let online = is_online(st, driver).await?;
    record_position(st, driver, &at, online).await?;
    if online {
        shift_seen(st, driver).await;
    }
    track(st, driver, &at).await;
    Ok(online)
}

/// Adds a fix to the trail of the ride the driver is working and sends it to the rider.
async fn track(st: &AppState, driver: Uuid, at: &LatLng) {
    let ride: Result<Option<(Uuid, Uuid, Option<Uuid>, String)>, _> = sqlx::query_as(&format!(
        "WITH r AS (
            SELECT id, rider_id, passenger_user_id, status FROM rides
            WHERE driver_id = $1 AND status IN {ACTIVE} LIMIT 1
         ), ins AS (
            INSERT INTO ride_track_points (ride_id, phase, location)
            SELECT id, CASE WHEN status = 'in_progress' THEN 'on_trip' ELSE 'to_pickup' END,
                   ST_SetSRID(ST_MakePoint($3, $2), 4326)::geography
            FROM r
            ON CONFLICT DO NOTHING
         )
         SELECT id, rider_id, passenger_user_id, status FROM r"
    ))
    .bind(driver)
    .bind(at.lat)
    .bind(at.lng)
    .fetch_optional(&st.pool)
    .await;
    // Reaching the destination ends the destination filter, as on Uber.
    let _ = sqlx::query(
        "UPDATE drivers SET destination = NULL, destination_name = NULL, destination_set_at = NULL
         WHERE user_id = $1 AND destination IS NOT NULL
           AND ST_DWithin(destination, ST_SetSRID(ST_MakePoint($3, $2), 4326)::geography, 1000)",
    )
    .bind(driver)
    .bind(at.lat)
    .bind(at.lng)
    .execute(&st.pool)
    .await;
    match ride {
        Ok(Some((ride, rider, passenger, status))) => {
            let eta = cached_eta(st, ride, driver, at, &status).await;
            let heading = at.heading.filter(|h| h.is_finite());
            let event = json!({
                "type": "driver_location", "ride_id": ride, "status": status, "lat": at.lat, "lng": at.lng,
                "heading": heading, "speed": at.speed.filter(|v| v.is_finite() && *v >= 0.0),
                "eta_seconds": eta.map(|e| e.0), "remaining_meters": eta.map(|e| e.1),
            });
            for user in std::iter::once(rider).chain(passenger) {
                st.hub.send(user, event.clone());
            }
        }
        Ok(None) => {}
        Err(err) => tracing::warn!(error = ?err, %driver, "could not record trail point"),
    }
}

// ---------- live ETA, route, surge map, destination ----------

/// Live ETA is re-routed with Google at most this often per ride (each call is billed).
const ETA_TTL_SECS: u64 = 30;

fn eta_key(ride: Uuid) -> String {
    format!("nova:ride:{ride}:eta")
}

/// Where the driver is heading next: the pickup until the trip starts, then the destination.
async fn next_stop(st: &AppState, ride: Uuid) -> Result<(&'static str, f64, f64), ApiError> {
    let (status, plat, plng, dlat, dlng): (String, f64, f64, f64, f64) = sqlx::query_as(
        "SELECT status, pickup_lat, pickup_lng, dropoff_lat, dropoff_lng FROM rides WHERE id = $1",
    )
    .bind(ride)
    .fetch_one(&st.pool)
    .await?;
    Ok(if status == "in_progress" { ("dropoff", dlat, dlng) } else { ("pickup", plat, plng) })
}

/// The last computed (seconds, metres) to the next stop. When it's stale, a fresh one is
/// computed in the background and goes out with the next ping, so pings never wait on Google.
async fn cached_eta(st: &AppState, ride: Uuid, driver: Uuid, at: &LatLng, status: &str) -> Option<(i64, i64)> {
    let mut redis = st.redis.clone();
    let raw: Option<String> = redis::cmd("GET").arg(eta_key(ride)).query_async(&mut redis).await.ok().flatten();
    let parsed = raw.as_deref().and_then(|r| r.split_once(',')).and_then(|(a, b)| Some((a.parse().ok()?, b.parse().ok()?)));
    if parsed.is_none() || status == "arriving" {
        // Claim the refresh so a burst of pings starts one Google call, not many.
        let claimed: bool = redis::cmd("SET").arg(format!("{}:lock", eta_key(ride))).arg("1").arg("NX").arg("EX").arg(ETA_TTL_SECS)
            .query_async::<Option<String>>(&mut redis).await.ok().flatten().is_some();
        if claimed && status != "arriving" {
            let st = st.clone();
            let origin = crate::geo::google::LatLng { lat: at.lat, lng: at.lng };
            tokio::spawn(async move {
                let _ = refresh_eta(&st, ride, driver, origin).await;
            });
        }
    }
    parsed
}

async fn refresh_eta(st: &AppState, ride: Uuid, _driver: Uuid, origin: crate::geo::google::LatLng) -> Result<crate::geo::google::Navigation, ApiError> {
    let key = crate::geo::maps_key(st)?;
    let (_, lat, lng) = next_stop(st, ride).await?;
    let nav = crate::geo::google::navigation_route(&st.http, key, origin, crate::geo::google::LatLng { lat, lng }).await?;
    let mut redis = st.redis.clone();
    let _: () = redis::cmd("SET").arg(eta_key(ride)).arg(format!("{},{}", nav.route.duration_seconds, nav.route.distance_meters))
        .arg("EX").arg(ETA_TTL_SECS * 2).query_async(&mut redis).await?;
    Ok(nav)
}

#[derive(Serialize)]
pub struct RideRoute {
    target: &'static str,
    eta_seconds: i64,
    distance_meters: i64,
    polyline: String,
    steps: Vec<crate::geo::google::Step>,
}

/// Turn-by-turn from the driver's last fix to the next stop (pickup, then destination).
async fn ride_route(State(st): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>) -> Result<Json<RideRoute>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    rate_limit(&st, "driver_route", driver, 30, 60).await?;
    let mine: bool = sqlx::query_scalar(&format!("SELECT EXISTS (SELECT 1 FROM rides WHERE id = $1 AND driver_id = $2 AND status IN {ACTIVE})"))
        .bind(id)
        .bind(driver)
        .fetch_one(&st.pool)
        .await?;
    if !mine {
        return Err(ApiError::NotFound("ride_not_found"));
    }
    let (lat, lng) = current_position(&st, driver).await?;
    let (target, _, _) = next_stop(&st, id).await?;
    let nav = refresh_eta(&st, id, driver, crate::geo::google::LatLng { lat, lng }).await?;
    Ok(Json(RideRoute {
        target,
        eta_seconds: nav.route.duration_seconds,
        distance_meters: nav.route.distance_meters,
        polyline: nav.route.encoded_polyline,
        steps: nav.steps,
    }))
}

#[derive(Deserialize)]
pub struct NearQuery {
    lat: f64,
    lng: f64,
}

#[derive(Serialize)]
pub struct SurgeCell {
    cell: String,
    multiplier: f64,
    /// Hexagon corners as [lat, lng], for drawing.
    boundary: Vec<[f64; 2]>,
}

/// Busy hexagons around the driver, for the heatmap: every cell within ~6 km whose surge is
/// above 1.0, with its outline. Same values riders are quoted with.
async fn surge_map(State(st): State<AppState>, headers: HeaderMap, axum::extract::Query(q): axum::extract::Query<NearQuery>) -> Result<Json<serde_json::Value>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    rate_limit(&st, "driver_surge", driver, 30, 60).await?;
    let centre = crate::geo::pricing::cell_for(q.lat, q.lng).ok_or(ApiError::BadRequest("invalid_location"))?;
    // Res-8 cells are ~0.8 km apart, so 7 rings reach about 6 km.
    let cells: Vec<h3o::CellIndex> = centre.grid_disk(7);
    let keys: Vec<String> = cells.iter().map(|c| format!("nova:surge:{c}")).collect();
    let mut redis = st.redis.clone();
    let values: Vec<Option<String>> = redis::cmd("MGET").arg(&keys).query_async(&mut redis).await?;
    let out: Vec<SurgeCell> = cells
        .into_iter()
        .zip(values)
        .filter_map(|(cell, v)| {
            let m: f64 = v?.parse().ok()?;
            (m > 1.0).then(|| SurgeCell {
                cell: cell.to_string(),
                multiplier: m,
                boundary: cell.boundary().iter().map(|ll| [ll.lat(), ll.lng()]).collect(),
            })
        })
        .collect();
    Ok(Json(json!({ "cells": out })))
}

#[derive(Serialize, FromRow)]
pub struct Destination {
    name: String,
    lat: f64,
    lng: f64,
}

async fn destination(st: &AppState, driver: Uuid) -> Result<Option<Destination>, ApiError> {
    Ok(sqlx::query_as::<_, Destination>(
        "SELECT destination_name AS name, ST_Y(destination::geometry) AS lat, ST_X(destination::geometry) AS lng
         FROM drivers WHERE user_id = $1 AND destination IS NOT NULL",
    )
    .bind(driver)
    .fetch_optional(&st.pool)
    .await?)
}

/// Destination sets left today: the limit of the area the driver is in (2 by default), counted
/// from local midnight in that area.
async fn destination_uses_left(st: &AppState, driver: Uuid) -> Result<i64, ApiError> {
    let here = last_location(st, driver).await;
    let (limit, tz): (i32, String) = match here {
        Some((lat, lng)) => match crate::geo::area_at(st, crate::geo::google::LatLng { lat, lng }).await? {
            Some(area) => sqlx::query_as("SELECT destination_uses_per_day, timezone FROM service_areas WHERE id = $1")
                .bind(area.id)
                .fetch_one(&st.pool)
                .await?,
            None => (2, "UTC".into()),
        },
        None => (2, "UTC".into()),
    };
    let used: i64 = sqlx::query_scalar(
        "SELECT count(*) FROM driver_destination_uses
         WHERE driver_id = $1 AND used_at >= (date_trunc('day', now() AT TIME ZONE $2) AT TIME ZONE $2)",
    )
    .bind(driver)
    .bind(&tz)
    .fetch_one(&st.pool)
    .await?;
    Ok((limit as i64 - used).max(0))
}

#[derive(Deserialize)]
pub struct DestinationReq {
    lat: f64,
    lng: f64,
    name: String,
}

async fn set_destination(State(st): State<AppState>, headers: HeaderMap, Json(req): Json<DestinationReq>) -> Result<Json<serde_json::Value>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    LatLng { lat: req.lat, lng: req.lng, heading: None, speed: None }.check()?;
    if !is_online(&st, driver).await? {
        return Err(ApiError::Conflict("not_online"));
    }
    let name: String = req.name.trim().chars().take(120).collect();
    if name.is_empty() {
        return Err(ApiError::BadRequest("invalid_place"));
    }
    if destination_uses_left(&st, driver).await? == 0 {
        return Err(ApiError::Conflict("destination_limit"));
    }
    let mut tx = st.pool.begin().await?;
    sqlx::query(
        "UPDATE drivers SET destination = ST_SetSRID(ST_MakePoint($3, $2), 4326)::geography, destination_name = $4,
                destination_set_at = now(), updated_at = now()
         WHERE user_id = $1",
    )
    .bind(driver)
    .bind(req.lat)
    .bind(req.lng)
    .bind(&name)
    .execute(&mut *tx)
    .await?;
    sqlx::query(
        "INSERT INTO driver_destination_uses (id, driver_id, name, location)
         VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($5, $4), 4326)::geography)",
    )
    .bind(Uuid::now_v7())
    .bind(driver)
    .bind(&name)
    .bind(req.lat)
    .bind(req.lng)
    .execute(&mut *tx)
    .await?;
    tx.commit().await?;
    Ok(Json(json!({
        "destination": destination(&st, driver).await?,
        "uses_left": destination_uses_left(&st, driver).await?,
    })))
}

async fn clear_destination(State(st): State<AppState>, headers: HeaderMap) -> Result<axum::http::StatusCode, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    sqlx::query("UPDATE drivers SET destination = NULL, destination_name = NULL, destination_set_at = NULL, updated_at = now() WHERE user_id = $1")
        .bind(driver)
        .execute(&st.pool)
        .await?;
    Ok(axum::http::StatusCode::NO_CONTENT)
}

// ---------- offers ----------

async fn accept(State(st): State<AppState>, headers: HeaderMap, Path(offer): Path<Uuid>) -> Result<Json<DriverRide>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    let mut tx = st.pool.begin().await?;
    let ride: Option<Uuid> = sqlx::query_scalar(
        "UPDATE ride_offers SET response = 'accepted', responded_at = now()
         WHERE id = $1 AND driver_id = $2 AND response IS NULL AND expires_at > now()
         RETURNING ride_id",
    )
    .bind(offer)
    .bind(driver)
    .fetch_optional(&mut *tx)
    .await?;
    let Some(ride) = ride else {
        return Err(ApiError::Conflict("offer_expired"));
    };
    let assigned = sqlx::query(
        "UPDATE rides SET status = 'accepted', driver_id = $2, accepted_at = now(), updated_at = now(),
                vehicle_id = (SELECT id FROM vehicles WHERE driver_id = $2 AND active)
         WHERE id = $1 AND status = 'requested'",
    )
    .bind(ride)
    .bind(driver)
    .execute(&mut *tx)
    .await;
    let assigned = match assigned {
        Ok(done) => done.rows_affected() == 1,
        Err(err) if is_unique_violation(&err) => {
            return Err(ApiError::Conflict("ride_in_progress"));
        }
        Err(err) => return Err(err.into()),
    };
    if !assigned {
        // The rider cancelled (or the request timed out) a moment before the tap.
        drop(tx);
        sqlx::query("UPDATE ride_offers SET response = 'withdrawn', responded_at = now() WHERE id = $1 AND response IS NULL")
            .bind(offer)
            .execute(&st.pool)
            .await?;
        st.dispatch.wake(ride);
        return Err(ApiError::Conflict("ride_unavailable"));
    }
    tx.commit().await?;
    st.dispatch.wake(ride);
    tracing::info!(%ride, %driver, "ride accepted");

    let (name, car): (String, String) = sqlx::query_as(
        "SELECT u.first_name, concat_ws(' ', v.color, v.make, v.model) || ' · ' || v.plate
         FROM users u JOIN vehicles v ON v.driver_id = u.id AND v.active WHERE u.id = $1",
    )
    .bind(driver)
    .fetch_one(&st.pool)
    .await
    .unwrap_or_else(|_| ("Your driver".into(), String::new()));
    dispatch::tell_riders(&st, ride, "accepted", Some(("driver_assigned", format!("{name} is on the way"), car))).await;
    driver_ride(&st, driver, Some(ride)).await?.map(Json).ok_or(ApiError::Internal)
}

async fn decline(State(st): State<AppState>, headers: HeaderMap, Path(offer): Path<Uuid>) -> Result<Json<DriverState>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    let ride: Option<Uuid> = sqlx::query_scalar(
        "UPDATE ride_offers SET response = 'declined', responded_at = now()
         WHERE id = $1 AND driver_id = $2 AND response IS NULL RETURNING ride_id",
    )
    .bind(offer)
    .bind(driver)
    .fetch_optional(&st.pool)
    .await?;
    if let Some(ride) = ride {
        st.dispatch.wake(ride);
    }
    Ok(Json(load_state(&st, driver).await?))
}

// ---------- working the ride ----------

/// Runs one status change for the driver's ride, or says why it can't happen.
async fn step(st: &AppState, driver: Uuid, ride: Uuid, sql: &str) -> Result<DriverRide, ApiError> {
    let moved = sqlx::query(sql).bind(ride).bind(driver).execute(&st.pool).await?.rows_affected() == 1;
    let current = driver_ride(st, driver, Some(ride)).await?.ok_or(ApiError::NotFound("ride_not_found"))?;
    if !moved {
        return Err(ApiError::Conflict(if current.status == "cancelled" { "ride_cancelled" } else { "invalid_ride_state" }));
    }
    Ok(current)
}

/// How far the driver is from the ride's pickup and destination, by PostGIS on the spheroid.
async fn distances(st: &AppState, ride: Uuid, driver: Uuid, at: (f64, f64)) -> Result<(i32, i32), ApiError> {
    sqlx::query_as(
        "SELECT ST_Distance(r.pickup, p.g)::int,
                ST_Distance(ST_SetSRID(ST_MakePoint(r.dropoff_lng, r.dropoff_lat), 4326)::geography, p.g)::int
         FROM rides r, (SELECT ST_SetSRID(ST_MakePoint($4, $3), 4326)::geography AS g) p
         WHERE r.id = $1 AND r.driver_id = $2",
    )
    .bind(ride)
    .bind(driver)
    .bind(at.0)
    .bind(at.1)
    .fetch_optional(&st.pool)
    .await?
    .ok_or(ApiError::NotFound("ride_not_found"))
}

/// Arrived only counts at the pickup: the GPS fix must be within the area's arrival radius
/// (320 m by default, Uber's 0.2 mi). The wait-time grace and the no-show clock start here.
async fn arrived(State(st): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>) -> Result<Json<DriverRide>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    let at = current_position(&st, driver).await?;
    let (to_pickup, _) = distances(&st, id, driver, at).await?;
    let card = fare::card(&st.pool, id).await?;
    if to_pickup > card.arrive_radius_m {
        return Err(ApiError::Conflict("not_at_pickup"));
    }
    let ride = step(&st, driver, id, &format!(
        "UPDATE rides SET status = 'arriving', arrived_at = now(), arrived_distance_m = {to_pickup}, updated_at = now()
         WHERE id = $1 AND driver_id = $2 AND status = 'accepted'")).await?;
    let name = first_name(&st, driver).await;
    dispatch::tell_riders(&st, id, "arriving", Some(("driver_arrived", format!("{name} has arrived"),
        format!("Meet them at {}", ride.pickup.name)))).await;
    Ok(Json(ride))
}

#[derive(Deserialize, Default)]
pub struct StartReq {
    pin: Option<String>,
}

/// Starts the trip once the rider's 4-digit PIN matches (rides from before PINs need none).
async fn start(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
    body: Option<Json<StartReq>>,
) -> Result<Json<DriverRide>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    // Ten tries an hour: enough for typos, far too few to guess 1 in 10,000.
    rate_limit(&st, "trip_pin", driver, 10, 3600).await?;
    let pin = body.and_then(|Json(b)| b.pin).unwrap_or_default();
    let expected: Option<Option<String>> = sqlx::query_scalar("SELECT trip_pin FROM rides WHERE id = $1 AND driver_id = $2")
        .bind(id)
        .bind(driver)
        .fetch_optional(&st.pool)
        .await?;
    match expected {
        None => return Err(ApiError::NotFound("ride_not_found")),
        Some(Some(expected)) if expected != pin.trim() => return Err(ApiError::BadRequest("wrong_pin")),
        Some(_) => {}
    }
    let ride = step(&st, driver, id,
        "UPDATE rides SET status = 'in_progress', started_at = now(), updated_at = now(),
                arrived_at = COALESCE(arrived_at, now())
         WHERE id = $1 AND driver_id = $2 AND status IN ('accepted', 'arriving')").await?;
    dispatch::tell_riders(&st, id, "in_progress", None).await;
    Ok(Json(ride))
}

/// Measured facts of a trip in progress, for settling it at drop-off.
#[derive(FromRow)]
struct TripFacts {
    upfront_cents: i64,
    surge: f64,
    route_meters: i64,
    route_seconds: i64,
    trip_seconds: i64,
    wait_seconds: i64,
    pickup_seconds: i64,
    trail_points: i64,
    trail_meters: i64,
    pickup_meters: i64,
    straight_meters: i64,
    dropoff_meters: i64,
}

/// Ends the trip where the driver is. The driver may end anywhere (a rider can ask to get out
/// early), but the fare follows what happened: the upfront price holds when the trip ends at the
/// destination as planned, and is recomputed on actual time and distance when it ends away from
/// the destination or runs much longer (Uber's rule). Wait time is added; the driver's
/// earnings and the service fee are fixed here.
async fn complete(State(st): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>) -> Result<Json<DriverRide>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    let at = current_position(&st, driver).await?;
    let card = fare::card(&st.pool, id).await?;
    // The end point closes the trail, so the measured line reaches where the trip ended.
    track(&st, driver, &LatLng { lat: at.0, lng: at.1, heading: None, speed: None }).await;
    let facts = sqlx::query_as::<_, TripFacts>(
        "WITH p AS (SELECT ST_SetSRID(ST_MakePoint($4, $3), 4326)::geography AS g),
              t AS (SELECT phase, count(*) AS n,
                           COALESCE(ST_Length(ST_MakeLine(location::geometry ORDER BY recorded_at)::geography), 0) AS m
                    FROM ride_track_points WHERE ride_id = $1 GROUP BY phase)
         SELECT COALESCE(r.upfront_fare_cents, r.fare_cents) AS upfront_cents, r.surge_multiplier AS surge,
                r.distance_meters AS route_meters, r.duration_seconds AS route_seconds,
                extract(epoch FROM now() - r.started_at)::bigint AS trip_seconds,
                COALESCE(extract(epoch FROM r.started_at - r.arrived_at), 0)::bigint AS wait_seconds,
                COALESCE(extract(epoch FROM r.arrived_at - r.accepted_at), 0)::bigint AS pickup_seconds,
                COALESCE((SELECT n FROM t WHERE phase = 'on_trip'), 0) AS trail_points,
                COALESCE((SELECT m FROM t WHERE phase = 'on_trip'), 0)::bigint AS trail_meters,
                COALESCE((SELECT m FROM t WHERE phase = 'to_pickup'), 0)::bigint AS pickup_meters,
                ST_Distance(r.pickup, p.g)::bigint AS straight_meters,
                ST_Distance(ST_SetSRID(ST_MakePoint(r.dropoff_lng, r.dropoff_lat), 4326)::geography, p.g)::bigint AS dropoff_meters
         FROM rides r, p
         WHERE r.id = $1 AND r.driver_id = $2 AND r.status = 'in_progress'",
    )
    .bind(id)
    .bind(driver)
    .bind(at.0)
    .bind(at.1)
    .fetch_optional(&st.pool)
    .await?;
    let Some(f) = facts else {
        let current = driver_ride(&st, driver, Some(id)).await?.ok_or(ApiError::NotFound("ride_not_found"))?;
        return Err(ApiError::Conflict(if current.status == "cancelled" { "ride_cancelled" } else { "invalid_ride_state" }));
    };
    // A sparse trail (app in the background, no signal) under-measures, so never use less than
    // the straight line from pickup to where the trip ended.
    let actual_meters = if f.trail_points >= 2 { f.trail_meters.max(f.straight_meters) } else { f.straight_meters };
    let trip = fare::Trip {
        upfront_cents: f.upfront_cents,
        surge: f.surge,
        route_meters: f.route_meters,
        route_seconds: f.route_seconds,
        actual_meters,
        actual_seconds: f.trip_seconds,
        dropoff_distance_m: f.dropoff_meters,
        wait_seconds: f.wait_seconds,
        pickup_seconds: f.pickup_seconds,
        pickup_meters: f.pickup_meters,
    };
    let money = fare::settle(&card, &trip);
    let moved = sqlx::query(
        "UPDATE rides SET status = 'completed', completed_at = now(), ended_at = now(), updated_at = now(),
                fare_cents = $3, fare_basis = $4, fare_change_reason = $5,
                actual_distance_meters = $6, actual_duration_seconds = $7, wait_seconds = $8,
                wait_fee_cents = $9, long_pickup_cents = $10, service_fee_cents = $11, driver_earnings_cents = $12,
                dropoff_distance_m = $13, ended_at_point = ST_SetSRID(ST_MakePoint($15, $14), 4326)::geography
         WHERE id = $1 AND driver_id = $2 AND status = 'in_progress'",
    )
    .bind(id)
    .bind(driver)
    .bind(money.fare_cents)
    .bind(money.basis)
    .bind(money.reason)
    .bind(actual_meters)
    .bind(f.trip_seconds)
    .bind(f.wait_seconds)
    .bind(money.wait_fee_cents)
    .bind(money.long_pickup_cents)
    .bind(money.service_fee_cents)
    .bind(money.driver_earnings_cents)
    .bind(f.dropoff_meters.min(i32::MAX as i64) as i32)
    .bind(at.0)
    .bind(at.1)
    .execute(&st.pool)
    .await?
    .rows_affected() == 1;
    let ride = driver_ride(&st, driver, Some(id)).await?.ok_or(ApiError::NotFound("ride_not_found"))?;
    if !moved {
        return Err(ApiError::Conflict("invalid_ride_state"));
    }
    let name = first_name(&st, driver).await;
    let body = match money.reason {
        Some("ended_away") => format!("{} with {name}. The trip ended before {}, so the fare is for the distance and time travelled.", ride.product_name, ride.dropoff.name),
        Some(_) => format!("{} to {} with {name}. The trip took much longer than planned, so the fare is for the actual distance and time.", ride.product_name, ride.dropoff.name),
        None => format!("{} to {} with {name}", ride.product_name, ride.dropoff.name),
    };
    dispatch::tell_riders(&st, id, "completed", Some(("ride_completed", "You've arrived".into(), body))).await;
    tracing::info!(ride = %id, %driver, fare = money.fare_cents, basis = money.basis, reason = ?money.reason, "ride completed");
    Ok(Json(ride))
}

#[derive(Deserialize, Default)]
pub struct CancelReq {
    reason: Option<String>,
}

/// The driver can't make it (before pickup only). The ride goes back to dispatch for another
/// driver instead of being lost; this driver isn't offered it again (ride_offers remembers).
/// With reason `no_show` (after the card's no-show wait, still at the pickup) the ride ends
/// instead and the rider pays the no-show fee.
async fn cancel(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
    body: Option<Json<CancelReq>>,
) -> Result<Json<DriverState>, ApiError> {
    let driver = require_driver(&st, &headers).await?;
    rate_limit(&st, "driver_cancel", driver, 10, 3600).await?;
    let reason = body.and_then(|Json(b)| b.reason).map(|r| r.trim().chars().take(200).collect::<String>());
    if reason.as_deref() == Some("no_show") {
        // A no-show needs the driver still at the pickup after the full wait, by GPS.
        let at = current_position(&st, driver).await?;
        let (to_pickup, _) = distances(&st, id, driver, at).await?;
        let card = fare::card(&st.pool, id).await?;
        if to_pickup > card.arrive_radius_m {
            return Err(ApiError::Conflict("not_at_pickup"));
        }
        let upfront: i64 = sqlx::query_scalar("SELECT COALESCE(upfront_fare_cents, fare_cents) FROM rides WHERE id = $1")
            .bind(id)
            .fetch_one(&st.pool)
            .await?;
        let (fee, service, earned) = fare::cancellation(&card, upfront);
        let ended: Option<String> = sqlx::query_scalar(
            "UPDATE rides SET status = 'cancelled', cancel_reason = 'rider_no_show', ended_at = now(), updated_at = now(),
                    cancel_fee_cents = $4, service_fee_cents = $5, driver_earnings_cents = $6
             WHERE id = $1 AND driver_id = $2 AND status = 'arriving'
               AND arrived_at <= now() - make_interval(secs => $3)
             RETURNING pickup_name",
        )
        .bind(id)
        .bind(driver)
        .bind(card.no_show_wait_secs as f64)
        .bind(fee)
        .bind(service)
        .bind(earned)
        .fetch_optional(&st.pool)
        .await?;
        let Some(pickup) = ended else {
            return Err(ApiError::Conflict("no_show_too_early"));
        };
        tracing::info!(ride = %id, %driver, "rider no-show");
        let name = first_name(&st, driver).await;
        dispatch::tell_riders(&st, id, "cancelled", Some(("rider_no_show", "Your driver couldn't find you".into(),
            format!("{name} waited {} min at {pickup}, so the trip was cancelled and a no-show fee applies.", card.no_show_wait_secs / 60)))).await;
        return Ok(Json(load_state(&st, driver).await?));
    }
    let moved = sqlx::query(
        "UPDATE rides SET status = 'requested', driver_id = NULL, vehicle_id = NULL,
                accepted_at = NULL, arrived_at = NULL, requested_at = now(), updated_at = now()
         WHERE id = $1 AND driver_id = $2 AND status IN ('accepted', 'arriving')",
    )
    .bind(id)
    .bind(driver)
    .execute(&st.pool)
    .await?
    .rows_affected() == 1;
    if !moved {
        return Err(ApiError::Conflict("invalid_ride_state"));
    }
    tracing::info!(ride = %id, %driver, reason = reason.as_deref().unwrap_or(""), "driver cancelled; redispatching");
    let name = first_name(&st, driver).await;
    dispatch::tell_riders(&st, id, "requested", Some(("driver_cancelled", "Finding you another driver".into(),
        format!("{name} couldn't make it. We're matching you with someone nearby.")))).await;
    dispatch::spawn(&st, id);
    Ok(Json(load_state(&st, driver).await?))
}

async fn first_name(st: &AppState, user: Uuid) -> String {
    sqlx::query_scalar("SELECT first_name FROM users WHERE id = $1")
        .bind(user)
        .fetch_one(&st.pool)
        .await
        .unwrap_or_else(|_| "Your driver".into())
}
