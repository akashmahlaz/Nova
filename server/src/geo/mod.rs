//! Geo and pricing API: place search, reverse geocoding, service areas and upfront fare quotes.
//!
//! Why the app calls us instead of Google directly (industry practice, Google's API security docs):
//!   * The Google key stays on the server, restricted by IP to these APIs. The Android app has its
//!     own key used only for drawing map tiles.
//!   * We authenticate every call and rate limit per user, so a leaked app or a script can't
//!     burn the Maps budget.
//!   * We can restrict and rank search results by the rider's service area, and later swap
//!     providers without shipping a new app.
//!
//! Google Maps Platform rules followed here:
//!   * Session tokens: autocomplete calls and the following Place Details share one token, which the
//!     app creates per search session (UUID v4) and drops after a pick.
//!   * Field masks: only the fields we render, so calls stay on the cheapest SKU.
//!   * Caching: Places content (names, addresses, coordinates) is not cached server side; place IDs
//!     may be stored indefinitely, so saved places keep their place ID.
//!
//! Scaling (what keeps cost and DB load flat as riders grow):
//!   * Service-area lookups run on every search and quote. Like Uber's geofence service (an in-memory
//!     index, not a DB hit per request), we memoise the answer per H3 cell in a `dashmap`, so a busy
//!     city resolves from RAM and PostGIS only sees the first lookup per ~0.1 km² cell.
//!   * PostGIS tests points against ST_Subdivide'd boundary pieces (migration 0008), so a lookup
//!     that does reach the DB is a small GiST hit with a cheap recheck.
//!   * Quotes are reused for the same rider and the same trip while still valid, so bouncing
//!     between screens does not pay for another traffic-aware Routes call.
//!   * Autocomplete is not cached: Google's terms forbid caching Places content (only place IDs,
//!     and coordinates for 30 days). The savings come from sessions (free when closed by Place
//!     Details), app-side debounce/min length, and showing saved places with zero API calls.
//!
//! Libraries: PostGIS (`geography` + GiST) answers "which service area is this point in";
//! geozero encodes `geo-types` points straight into sqlx parameters; h3o buckets pickups into
//! hexagons for surge (see `pricing.rs`); Redis holds locked quotes and rate-limit counters.

pub mod google;
pub mod pricing;

use std::{
    sync::Arc,
    time::{Duration, Instant},
};

use dashmap::DashMap;
use axum::{
    extract::{Path, Query, State},
    http::HeaderMap,
    routing::{get, post},
    Json, Router,
};
use garde::Validate;
use geozero::wkb;
use redis::AsyncCommands;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use sqlx::FromRow;
use uuid::Uuid;

use crate::{
    auth::{require_user_id, validation_error},
    error::ApiError,
    AppState,
};
use google::LatLng;
use pricing::{FareOption, FareRate};

/// How long an upfront price is honoured. Long enough to choose a ride type, short enough that
/// traffic and surge haven't moved much.
const QUOTE_TTL_SECS: u64 = 5 * 60;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/geo/autocomplete", post(autocomplete))
        .route("/v1/geo/cities", get(cities))
        .route("/v1/geo/places/{place_id}", get(place_details))
        .route("/v1/geo/reverse", get(reverse))
        .route("/v1/geo/nearby", get(nearby))
        .route("/v1/geo/area", get(area))
        .route("/v1/rides/quote", post(quote))
}

// ---------- shared ----------

#[derive(Debug, Clone, Copy, Deserialize, Serialize, Validate)]
pub struct Point {
    #[garde(range(min = -90.0, max = 90.0))]
    pub lat: f64,
    #[garde(range(min = -180.0, max = 180.0))]
    pub lng: f64,
}

impl Point {
    fn checked(self) -> Result<LatLng, ApiError> {
        // garde ranges let NaN through (every comparison with NaN is false), so check finiteness too.
        if !self.lat.is_finite() || !self.lng.is_finite() || self.validate().is_err() {
            return Err(ApiError::BadRequest("invalid_location"));
        }
        Ok(LatLng { lat: self.lat, lng: self.lng })
    }
}

#[derive(Debug, Clone, Serialize, FromRow)]
pub struct ServiceArea {
    pub id: Uuid,
    pub name: String,
    pub region_code: String,
    pub currency: String,
    /// Highest surge allowed here (a legal cap in some countries).
    pub max_surge: f64,
}

/// Per-process memo of "which service area is this H3 cell in".
///
/// Keyed by an H3 res-9 cell (~0.1 km², edge ~175 m). Points near an area edge may share a cell
/// with points just outside, so the answer can be off by up to one cell width at the boundary,
/// which is acceptable for "do we operate here". Entries expire so boundary edits show up within
/// [AREA_CACHE_TTL], and the map is cleared if it ever grows past [AREA_CACHE_MAX] cells.
#[derive(Clone, Default)]
pub struct AreaCache(Arc<DashMap<u64, (Option<ServiceArea>, Instant)>>);

impl AreaCache {
    /// Forget every cached area, e.g. after an admin changes an area's rules.
    pub fn clear(&self) {
        self.0.clear();
    }
}

const AREA_CACHE_TTL: Duration = Duration::from_secs(5 * 60);
const AREA_CACHE_MAX: usize = 200_000;
const AREA_CACHE_RES: h3o::Resolution = h3o::Resolution::Nine;

pub(crate) fn maps_key(st: &AppState) -> Result<&str, ApiError> {
    st.cfg.google_maps_api_key.as_deref().ok_or(ApiError::Unavailable("maps_unavailable"))
}

/// Fixed-window per-user rate limit (INCR + EXPIRE on first hit). Returns 429 with the seconds
/// left in the window once `limit` is exceeded.
pub(crate) async fn rate_limit(st: &AppState, bucket: &str, user: Uuid, limit: i64, window_secs: i64) -> Result<(), ApiError> {
    let mut redis = st.redis.clone();
    let key = format!("nova:rl:{bucket}:{user}");
    let (count, ttl): (i64, i64) = redis::pipe()
        .incr(&key, 1)
        .ttl(&key)
        .query_async(&mut redis)
        .await?;
    if ttl < 0 {
        let _: () = redis.expire(&key, window_secs).await?;
    }
    if count > limit {
        return Err(ApiError::TooManyRequests(if ttl > 0 { ttl } else { window_secs }));
    }
    Ok(())
}

/// The active service area covering a point: memory first, then PostGIS.
pub async fn area_at(st: &AppState, at: LatLng) -> Result<Option<ServiceArea>, ApiError> {
    let cell = h3o::LatLng::new(at.lat, at.lng)
        .map_err(|_| ApiError::BadRequest("invalid_location"))?
        .to_cell(AREA_CACHE_RES);
    let key = u64::from(cell);
    if let Some(hit) = st.areas.0.get(&key) {
        if hit.1.elapsed() < AREA_CACHE_TTL {
            return Ok(hit.0.clone());
        }
    }

    // geo-types uses (x, y) = (lng, lat). geozero writes it as EWKB; we stamp SRID 4326 (WGS84,
    // what GPS and Google return). The subdivided parts are planar geometry with small, densified
    // edges, so a planar ST_Intersects is both fast (index-assisted) and accurate here.
    let point: geo_types::Geometry<f64> = geo_types::Point::new(at.lng, at.lat).into();
    let row = sqlx::query_as::<_, ServiceArea>(
        "SELECT a.id, a.name, a.region_code, a.currency, a.max_surge
         FROM service_area_parts p
         JOIN service_areas a ON a.id = p.service_area_id
         WHERE a.active AND ST_Intersects(p.part, ST_SetSRID($1::geometry, 4326))
         ORDER BY ST_Area(a.boundary) ASC
         LIMIT 1",
    )
    .bind(wkb::Encode(point))
    .fetch_optional(&st.pool)
    .await?;

    if st.areas.0.len() >= AREA_CACHE_MAX {
        st.areas.0.clear();
    }
    st.areas.0.insert(key, (row.clone(), Instant::now()));
    Ok(row)
}

/// Google session tokens must be URL-safe base64 of at most 36 chars; a UUID string qualifies.
fn valid_session_token(token: &str) -> bool {
    !token.is_empty()
        && token.len() <= 36
        && token.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
}

// ---------- search ----------

#[derive(Deserialize, Validate)]
pub struct AutocompleteReq {
    #[garde(length(min = 1, max = 120))]
    input: String,
    #[garde(length(min = 1, max = 36))]
    session_token: String,
    /// Rider's current position, used for ranking and distances.
    #[garde(dive)]
    near: Option<Point>,
}

#[derive(Serialize)]
pub struct AutocompleteRes {
    suggestions: Vec<google::Suggestion>,
}

async fn autocomplete(
    State(st): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<AutocompleteReq>,
) -> Result<Json<AutocompleteRes>, ApiError> {
    let user = require_user_id(&st, &headers)?;
    req.validate().map_err(validation_error)?;
    if !valid_session_token(&req.session_token) {
        return Err(ApiError::BadRequest("invalid_session_token"));
    }
    let input = req.input.trim();
    if input.is_empty() {
        return Ok(Json(AutocompleteRes { suggestions: vec![] }));
    }
    // The app debounces typing (~300 ms), so 120/min is generous for a human and tight for a script.
    rate_limit(&st, "autocomplete", user, 120, 60).await?;
    let key = maps_key(&st)?;

    let near = req.near.map(Point::checked).transpose()?;
    // Limit results to the country of the rider's service area, when we know it.
    let area = match near {
        Some(at) => area_at(&st, at).await?,
        None => None,
    };

    let suggestions = google::autocomplete(
        &st.http,
        key,
        google::AutocompleteInput {
            input,
            session_token: &req.session_token,
            near,
            region_code: area.as_ref().map(|a| a.region_code.as_str()),
        },
    )
    .await?;
    Ok(Json(AutocompleteRes { suggestions }))
}

#[derive(Deserialize)]
pub struct CitiesQuery {
    country: Option<String>,
    country_name: Option<String>,
    q: Option<String>,
    lat: Option<f64>,
    lng: Option<f64>,
    session_token: Option<String>,
}

#[derive(Serialize)]
pub struct CityItem {
    name: String,
    region: String,
    country_code: String,
}

#[derive(Serialize)]
pub struct CitiesRes {
    cities: Vec<CityItem>,
}

#[derive(Deserialize, Serialize)]
struct IpGeo {
    lat: f64,
    lng: f64,
    city: String,
    region: String,
    country_code: String,
}

fn extract_client_ip(headers: &HeaderMap) -> Option<String> {
    let raw = headers
        .get("cf-connecting-ip")
        .or_else(|| headers.get("x-real-ip"))
        .or_else(|| headers.get("x-forwarded-for"))
        .and_then(|v| v.to_str().ok())?;
    let first = raw.split(',').next()?.trim();
    let ip: std::net::IpAddr = first.parse().ok()?;
    let is_private = match ip {
        std::net::IpAddr::V4(v4) => v4.is_private() || v4.is_loopback() || v4.is_link_local(),
        std::net::IpAddr::V6(v6) => v6.is_loopback() || v6.is_unique_local() || v6.is_unicast_link_local(),
    };
    if is_private { None } else { Some(ip.to_string()) }
}

async fn resolve_ip_geo(st: &AppState, headers: &HeaderMap) -> Option<IpGeo> {
    let client_ip = extract_client_ip(headers);
    let cache_key = format!("nova:ipgeo:{}", client_ip.as_deref().unwrap_or("self"));
    let mut redis = st.redis.clone();
    if let Ok(Some(cached)) = redis.get::<_, Option<String>>(&cache_key).await {
        if let Ok(parsed) = serde_json::from_str::<IpGeo>(&cached) {
            return Some(parsed);
        }
    }
    let url = match &client_ip {
        Some(ip) => format!("https://ipwho.is/{ip}"),
        None => "https://ipwho.is/".to_string(),
    };
    let resp = st
        .http
        .get(url)
        .timeout(std::time::Duration::from_secs(3))
        .send()
        .await
        .ok()?;
    if !resp.status().is_success() {
        return None;
    }
    let v: serde_json::Value = resp.json().await.ok()?;
    if v.get("success").and_then(|s| s.as_bool()) == Some(false) {
        return None;
    }
    let lat = v.get("latitude").and_then(|x| x.as_f64())?;
    let lng = v.get("longitude").and_then(|x| x.as_f64())?;
    let city = v.get("city").and_then(|x| x.as_str()).unwrap_or("").trim().to_string();
    let region = v.get("region").and_then(|x| x.as_str()).unwrap_or("").trim().to_string();
    let country_code = v.get("country_code").and_then(|x| x.as_str()).unwrap_or("").trim().to_ascii_uppercase();
    let geo = IpGeo { lat, lng, city, region, country_code };
    if let Ok(json_str) = serde_json::to_string(&geo) {
        let _: Result<(), _> = redis.set_ex(&cache_key, json_str, 6 * 3600).await;
    }
    Some(geo)
}

/// Worldwide city search for driver onboarding:
/// - `q` present -> Google Places `(cities)` autocomplete filtered by `country`
/// - `q` empty + `lat`/`lng` present -> Google Places `searchNearby` (`locality`) around GPS
/// - `q` empty + no GPS -> IP geolocation (`ipwho.is`, cached in Redis) + `searchNearby` when in
///   the same country, falling back to `searchText` (`locality`) for `country_name` / `country`
///
/// NOTE: Never query `service_areas` here — `service_areas` are pricing/dispatch geofences
/// (e.g. "Punjab (dev)"), not a worldwide list of driver cities.
async fn cities(
    State(st): State<AppState>,
    headers: HeaderMap,
    Query(query): Query<CitiesQuery>,
) -> Result<Json<CitiesRes>, ApiError> {
    rate_limit(&st, "geo_cities", Uuid::nil(), 240, 60).await?;
    let country = query
        .country
        .as_deref()
        .map(str::trim)
        .filter(|c| c.len() == 2 && c.chars().all(|ch| ch.is_ascii_alphabetic()))
        .map(|c| c.to_ascii_uppercase());
    let q = query.q.as_deref().map(str::trim).unwrap_or("");
    let key = maps_key(&st)?;

    if q.is_empty() {
        let gps = match (query.lat, query.lng) {
            (Some(lat), Some(lng)) if (-90.0..=90.0).contains(&lat) && (-180.0..=180.0).contains(&lng) => Some((lat, lng)),
            _ => None,
        };
        let ip_geo = if gps.is_none() { resolve_ip_geo(&st, &headers).await } else { None };
        let ip_matches_country = match (&ip_geo, &country) {
            (Some(g), Some(c)) => g.country_code.is_empty() || g.country_code == *c,
            (Some(_), None) => true,
            _ => false,
        };

        let mut cities: Vec<CityItem> = if let Some((lat, lng)) = gps {
            google::nearby_cities(&st.http, key, lat, lng)
                .await?
                .into_iter()
                .map(|p| CityItem {
                    name: p.name,
                    region: p.region,
                    country_code: country.clone().unwrap_or_default(),
                })
                .collect()
        } else if let (Some(g), true) = (&ip_geo, ip_matches_country) {
            let mut list: Vec<CityItem> = google::nearby_cities(&st.http, key, g.lat, g.lng)
                .await
                .unwrap_or_default()
                .into_iter()
                .map(|p| CityItem {
                    name: p.name,
                    region: p.region,
                    country_code: country.clone().unwrap_or_else(|| g.country_code.clone()),
                })
                .collect();
            if !g.city.is_empty() && !list.iter().any(|c| c.name.eq_ignore_ascii_case(&g.city)) {
                list.insert(
                    0,
                    CityItem {
                        name: g.city.clone(),
                        region: g.region.clone(),
                        country_code: country.clone().unwrap_or_else(|| g.country_code.clone()),
                    },
                );
            }
            list
        } else {
            let cname = query
                .country_name
                .as_deref()
                .map(str::trim)
                .filter(|s| !s.is_empty())
                .or(country.as_deref())
                .unwrap_or("India");
            google::country_cities(&st.http, key, cname, country.as_deref())
                .await?
                .into_iter()
                .map(|p| CityItem {
                    name: p.name,
                    region: p.region,
                    country_code: country.clone().unwrap_or_default(),
                })
                .collect()
        };
        if cities.is_empty() {
            if let Some(g) = ip_geo.filter(|_| ip_matches_country) {
                if !g.city.is_empty() {
                    cities.push(CityItem {
                        name: g.city,
                        region: g.region,
                        country_code: country.unwrap_or(g.country_code),
                    });
                }
            }
        }
        let mut seen = std::collections::HashSet::new();
        cities.retain(|c| seen.insert(c.name.to_ascii_lowercase()));
        return Ok(Json(CitiesRes { cities }));
    }
    let token = query
        .session_token
        .as_deref()
        .filter(|t| valid_session_token(t))
        .unwrap_or("driver-city-picker-session");
    let suggestions = google::autocomplete_cities(&st.http, key, q, country.as_deref(), token).await?;
    let cities = suggestions
        .into_iter()
        .filter(|s| !s.primary_text.is_empty())
        .map(|s| CityItem {
            name: s.primary_text,
            region: s.secondary_text,
            country_code: country.clone().unwrap_or_default(),
        })
        .collect();
    Ok(Json(CitiesRes { cities }))
}

#[derive(Deserialize)]
pub struct DetailsQuery {
    session_token: Option<String>,
}

async fn place_details(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(place_id): Path<String>,
    Query(q): Query<DetailsQuery>,
) -> Result<Json<google::PlaceDetails>, ApiError> {
    let user = require_user_id(&st, &headers)?;
    // Place IDs are URL-safe tokens; rejecting anything else keeps the upstream path clean.
    if place_id.is_empty()
        || place_id.len() > 512
        || !place_id.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
    {
        return Err(ApiError::BadRequest("invalid_place_id"));
    }
    let token = q.session_token.filter(|t| valid_session_token(t));
    rate_limit(&st, "place_details", user, 30, 60).await?;
    let key = maps_key(&st)?;
    Ok(Json(google::place_details(&st.http, key, &place_id, token.as_deref()).await?))
}

async fn reverse(
    State(st): State<AppState>,
    headers: HeaderMap,
    Query(at): Query<Point>,
) -> Result<Json<google::PlaceDetails>, ApiError> {
    let user = require_user_id(&st, &headers)?;
    let at = at.checked()?;
    rate_limit(&st, "reverse", user, 30, 60).await?;
    let key = maps_key(&st)?;
    google::reverse_geocode(&st.http, key, at)
        .await?
        .map(Json)
        .ok_or(ApiError::NotFound("no_address"))
}

#[derive(Serialize)]
pub struct NearbyRes {
    places: Vec<google::PlaceDetails>,
}

/// Closest places to the rider (300 m, nearest first), for suggestions before typing.
/// Pro-SKU call: the app requests it once per search screen visit, and this limit backs that up.
async fn nearby(
    State(st): State<AppState>,
    headers: HeaderMap,
    Query(at): Query<Point>,
) -> Result<Json<NearbyRes>, ApiError> {
    let user = require_user_id(&st, &headers)?;
    let at = at.checked()?;
    rate_limit(&st, "nearby", user, 10, 60).await?;
    let key = maps_key(&st)?;
    let places = google::nearby(&st.http, key, at, 300.0, 8).await?;
    Ok(Json(NearbyRes { places }))
}

async fn area(
    State(st): State<AppState>,
    headers: HeaderMap,
    Query(at): Query<Point>,
) -> Result<Json<ServiceArea>, ApiError> {
    require_user_id(&st, &headers)?;
    let at = at.checked()?;
    area_at(&st, at).await?.map(Json).ok_or(ApiError::NotFound("outside_service_area"))
}

// ---------- quotes ----------

#[derive(Deserialize, Validate)]
pub struct QuoteReq {
    #[garde(dive)]
    pickup: Point,
    #[garde(dive)]
    dropoff: Point,
    /// Up to 3 intermediate stops, in order.
    #[garde(length(max = 3), dive)]
    #[serde(default)]
    stops: Vec<Point>,
}

#[derive(Serialize)]
pub struct QuoteRes {
    quote_id: Uuid,
    expires_in: u64,
    service_area: String,
    currency: String,
    distance_meters: i64,
    duration_seconds: i64,
    /// Google encoded polyline of the route, for drawing on the map.
    polyline: String,
    surge_multiplier: f64,
    options: Vec<FareOption>,
}

/// What we lock in Redis. A ride request (phase 3) loads this by id, checks `user_id`, and books
/// at these prices, so the app never sends a price the server has to trust.
#[derive(Serialize, Deserialize)]
pub(crate) struct StoredQuote {
    pub user_id: Uuid,
    pub service_area_id: Uuid,
    pub currency: String,
    pub pickup: Point,
    pub dropoff: Point,
    pub stops: Vec<Point>,
    pub distance_meters: i64,
    pub duration_seconds: i64,
    pub h3_cell: String,
    pub surge_multiplier: f64,
    pub polyline: String,
    pub service_area: String,
    pub options: Vec<FareOption>,
}

/// Loads a locked quote. Returns `quote_expired` when it timed out (or never existed), and the same
/// code when it belongs to someone else, so quote ids can't be probed.
pub(crate) async fn load_quote(st: &AppState, user: Uuid, quote_id: Uuid) -> Result<StoredQuote, ApiError> {
    let mut redis = st.redis.clone();
    let payload: Option<String> = redis.get(format!("nova:quote:{quote_id}")).await?;
    let stored = payload
        .and_then(|p| serde_json::from_str::<StoredQuote>(&p).ok())
        .ok_or(ApiError::Conflict("quote_expired"))?;
    if stored.user_id != user {
        return Err(ApiError::Conflict("quote_expired"));
    }
    Ok(stored)
}

/// Drops a quote once it has been booked, so it can't be reused for a second ride.
pub(crate) async fn consume_quote(st: &AppState, quote_id: Uuid) -> Result<(), ApiError> {
    let mut redis = st.redis.clone();
    let _: () = redis.del(format!("nova:quote:{quote_id}")).await?;
    Ok(())
}

impl StoredQuote {
    fn response(self, quote_id: Uuid, expires_in: u64) -> QuoteRes {
        QuoteRes {
            quote_id,
            expires_in,
            service_area: self.service_area,
            currency: self.currency,
            distance_meters: self.distance_meters,
            duration_seconds: self.duration_seconds,
            polyline: self.polyline,
            surge_multiplier: self.surge_multiplier,
            options: self.options,
        }
    }
}

/// Stable key for "this rider, this trip". Coordinates are rounded to 4 decimals (~11 m) so GPS
/// jitter between two screens still counts as the same trip.
fn trip_fingerprint(user: Uuid, pickup: Point, dropoff: Point, stops: &[Point]) -> String {
    let mut h = Sha256::new();
    h.update(user.as_bytes());
    for p in std::iter::once(&pickup).chain(stops).chain(std::iter::once(&dropoff)) {
        h.update(format!("{:.4},{:.4};", p.lat, p.lng));
    }
    hex::encode(h.finalize())
}

/// Reuse a still-valid quote only if it has at least this long left, so the rider isn't
/// shown a price that expires while they decide.
const QUOTE_REUSE_MIN_SECS: i64 = 90;

async fn quote(
    State(st): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<QuoteReq>,
) -> Result<Json<QuoteRes>, ApiError> {
    let user = require_user_id(&st, &headers)?;
    req.validate().map_err(validation_error)?;
    let pickup = req.pickup.checked()?;
    let dropoff = req.dropoff.checked()?;
    let stops = req.stops.iter().map(|p| p.checked()).collect::<Result<Vec<_>, _>>()?;
    // Each fresh quote is a paid Routes call.
    rate_limit(&st, "quote", user, 20, 60).await?;
    let key = maps_key(&st)?;

    // 0. Same rider, same trip, quote still fresh: return it instead of re-routing.
    let mut redis = st.redis.clone();
    let dedupe_key = format!(
        "nova:quote:trip:{}",
        trip_fingerprint(user, req.pickup, req.dropoff, &req.stops)
    );
    if let Some(existing) = redis.get::<_, Option<String>>(&dedupe_key).await? {
        let quote_key = format!("nova:quote:{existing}");
        let (payload, ttl): (Option<String>, i64) =
            redis::pipe().get(&quote_key).ttl(&quote_key).query_async(&mut redis).await?;
        if let (Some(payload), Ok(id)) = (payload, Uuid::parse_str(&existing)) {
            if ttl >= QUOTE_REUSE_MIN_SECS {
                if let Ok(stored) = serde_json::from_str::<StoredQuote>(&payload) {
                    return Ok(Json(stored.response(id, ttl as u64)));
                }
            }
        }
    }

    // 1. The pickup must be somewhere we operate. Drop-offs may leave the area (airport runs).
    let area = area_at(&st, pickup).await?.ok_or(ApiError::BadRequest("outside_service_area"))?;

    // 2. Price cards for this area.
    let rates = sqlx::query_as::<_, FareRate>(
        "SELECT p.id AS product_id, p.name, p.seats, p.description,
                r.base_cents, r.per_km_cents, r.per_min_cents, r.minimum_cents, r.booking_fee_cents,
                a.fare_rounding_minor AS rounding_minor
         FROM fare_rates r
         JOIN ride_products p ON p.id = r.product_id
         JOIN service_areas a ON a.id = r.service_area_id
         WHERE r.service_area_id = $1 AND r.active
         ORDER BY p.sort_order",
    )
    .bind(area.id)
    .fetch_all(&st.pool)
    .await?;
    if rates.is_empty() {
        return Err(ApiError::BadRequest("no_products"));
    }

    // 3. Traffic-aware route: the distance and duration we price on.
    let route = google::compute_route(&st.http, key, pickup, &stops, dropoff).await?;

    // 4. Surge for the pickup hexagon, and count this request as demand in that hexagon.
    let cell = pricing::cell_for(pickup.lat, pickup.lng).ok_or(ApiError::BadRequest("invalid_location"))?;
    let surge = pricing::surge_multiplier(&mut redis, cell).await?.min(area.max_surge.max(1.0));
    pricing::record_demand(&mut redis, cell).await?;

    let options: Vec<FareOption> = rates
        .iter()
        .map(|r| FareOption {
            product_id: r.product_id.clone(),
            name: r.name.clone(),
            seats: r.seats,
            description: r.description.clone(),
            fare_cents: pricing::fare_cents(r, route.distance_meters, route.duration_seconds, surge),
        })
        .collect();

    // 5. Lock the prices, and remember this trip -> quote for reuse.
    let quote_id = Uuid::now_v7();
    let stored = StoredQuote {
        user_id: user,
        service_area_id: area.id,
        currency: area.currency.clone(),
        pickup: req.pickup,
        dropoff: req.dropoff,
        stops: req.stops.clone(),
        distance_meters: route.distance_meters,
        duration_seconds: route.duration_seconds,
        h3_cell: cell.to_string(),
        surge_multiplier: surge,
        polyline: route.encoded_polyline,
        service_area: area.name,
        options,
    };
    let payload = serde_json::to_string(&stored).map_err(|_| ApiError::Internal)?;
    let _: () = redis::pipe()
        .set_ex(format!("nova:quote:{quote_id}"), payload, QUOTE_TTL_SECS)
        .set_ex(&dedupe_key, quote_id.to_string(), QUOTE_TTL_SECS)
        .query_async(&mut redis)
        .await?;

    Ok(Json(stored.response(quote_id, QUOTE_TTL_SECS)))
}
