//! Thin Google Maps Platform client: Places API (New), Routes API, Geocoding API.
//!
//! Best practices applied (from Google's docs, see `geo/mod.rs` for the list):
//!   * Every call sends an explicit `X-Goog-FieldMask` with only the fields we use. Field masks are
//!     mandatory for Routes, and on Places the mask decides the billing SKU, so `*` would cost more
//!     and get slower whenever Google adds fields.
//!   * Autocomplete and the following Place Details share one session token, so the whole
//!     "type, type, type, pick" session is billed as a single session instead of per keystroke.
//!   * The key never leaves the server; the Android app only talks to our API.
//!   * Short per-request timeouts: a slow Google call must not hold an API worker for 15s.

use std::time::Duration;

use serde::Deserialize;
use serde_json::{json, Value};

use crate::error::ApiError;

const PLACES_BASE: &str = "https://places.googleapis.com/v1";
const ROUTES_URL: &str = "https://routes.googleapis.com/directions/v2:computeRoutes";
const GEOCODE_URL: &str = "https://maps.googleapis.com/maps/api/geocode/json";
const TIMEOUT: Duration = Duration::from_secs(5);

/// Max radius Places accepts for a circle location bias, in metres.
const MAX_BIAS_RADIUS_M: f64 = 50_000.0;

#[derive(Debug, Clone, Copy)]
pub struct LatLng {
    pub lat: f64,
    pub lng: f64,
}

impl LatLng {
    fn to_json(self) -> Value {
        json!({ "latitude": self.lat, "longitude": self.lng })
    }

    /// Routes API waypoint shape.
    fn waypoint(self) -> Value {
        json!({ "location": { "latLng": self.to_json() } })
    }
}

#[derive(Debug, serde::Serialize)]
pub struct Suggestion {
    pub place_id: String,
    pub primary_text: String,
    pub secondary_text: String,
    /// Straight-line distance from the rider, when we sent an origin.
    pub distance_meters: Option<i64>,
}

#[derive(Debug, serde::Serialize)]
pub struct PlaceDetails {
    pub place_id: String,
    pub name: String,
    pub address: String,
    pub lat: f64,
    pub lng: f64,
}

#[derive(Debug)]
pub struct Route {
    pub distance_meters: i64,
    pub duration_seconds: i64,
    /// Google encoded polyline; the app decodes it to draw the route.
    pub encoded_polyline: String,
}

pub struct AutocompleteInput<'a> {
    pub input: &'a str,
    pub session_token: &'a str,
    /// Rider position: biases ranking and returns distances.
    pub near: Option<LatLng>,
    /// Restrict suggestions to the rider's service-area country.
    pub region_code: Option<&'a str>,
}

/// Places Autocomplete (New). One request per (debounced) keystroke, all under one session token.
pub async fn autocomplete(
    http: &reqwest::Client,
    key: &str,
    q: AutocompleteInput<'_>,
) -> Result<Vec<Suggestion>, ApiError> {
    let mut body = json!({
        "input": q.input,
        "sessionToken": q.session_token,
    });
    if let Some(near) = q.near {
        body["locationBias"] = json!({ "circle": { "center": near.to_json(), "radius": MAX_BIAS_RADIUS_M } });
        body["origin"] = near.to_json();
    }
    if let Some(region) = q.region_code {
        body["includedRegionCodes"] = json!([region]);
        body["regionCode"] = json!(region.to_ascii_lowercase());
    }

    let res = http
        .post(format!("{PLACES_BASE}/places:autocomplete"))
        .timeout(TIMEOUT)
        .header("X-Goog-Api-Key", key)
        .header(
            "X-Goog-FieldMask",
            "suggestions.placePrediction.placeId,\
             suggestions.placePrediction.structuredFormat,\
             suggestions.placePrediction.distanceMeters",
        )
        .json(&body)
        .send()
        .await;
    let v = read_json(res, "places autocomplete").await?;

    #[derive(Deserialize)]
    struct Text {
        #[serde(default)]
        text: String,
    }
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Structured {
        main_text: Option<Text>,
        secondary_text: Option<Text>,
    }
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Prediction {
        place_id: String,
        structured_format: Option<Structured>,
        distance_meters: Option<i64>,
    }
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Item {
        place_prediction: Option<Prediction>,
    }
    #[derive(Deserialize)]
    struct Res {
        #[serde(default)]
        suggestions: Vec<Item>,
    }

    let parsed: Res = serde_json::from_value(v).map_err(|e| bad_shape("places autocomplete", e))?;
    Ok(parsed
        .suggestions
        .into_iter()
        .filter_map(|s| s.place_prediction)
        .map(|p| {
            let (primary, secondary) = p
                .structured_format
                .map(|f| {
                    (
                        f.main_text.map(|t| t.text).unwrap_or_default(),
                        f.secondary_text.map(|t| t.text).unwrap_or_default(),
                    )
                })
                .unwrap_or_default();
            Suggestion {
                place_id: p.place_id,
                primary_text: primary,
                secondary_text: secondary,
                distance_meters: p.distance_meters,
            }
        })
        .collect())
}

/// Places Autocomplete (New) restricted to `(cities)` for worldwide driver onboarding.
pub async fn autocomplete_cities(
    http: &reqwest::Client,
    key: &str,
    input: &str,
    region_code: Option<&str>,
    session_token: &str,
) -> Result<Vec<Suggestion>, ApiError> {
    let mut body = json!({
        "input": input,
        "sessionToken": session_token,
        "includedPrimaryTypes": ["(cities)"],
    });
    if let Some(region) = region_code {
        body["includedRegionCodes"] = json!([region]);
        body["regionCode"] = json!(region.to_ascii_lowercase());
    }

    let res = http
        .post(format!("{PLACES_BASE}/places:autocomplete"))
        .timeout(TIMEOUT)
        .header("X-Goog-Api-Key", key)
        .header(
            "X-Goog-FieldMask",
            "suggestions.placePrediction.placeId,\
             suggestions.placePrediction.structuredFormat",
        )
        .json(&body)
        .send()
        .await;
    let v = read_json(res, "places city autocomplete").await?;

    #[derive(Deserialize)]
    struct Text {
        #[serde(default)]
        text: String,
    }
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Structured {
        main_text: Option<Text>,
        secondary_text: Option<Text>,
    }
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Prediction {
        place_id: String,
        structured_format: Option<Structured>,
    }
    #[derive(Deserialize)]
    struct Item {
        #[serde(rename = "placePrediction")]
        place_prediction: Option<Prediction>,
    }
    #[derive(Deserialize)]
    struct Res {
        #[serde(default)]
        suggestions: Vec<Item>,
    }
    let parsed: Res = serde_json::from_value(v).map_err(|e| bad_shape("places city autocomplete", e))?;
    Ok(parsed
        .suggestions
        .into_iter()
        .filter_map(|s| s.place_prediction)
        .map(|p| {
            let (primary, secondary) = p
                .structured_format
                .map(|f| {
                    (
                        f.main_text.map(|t| t.text).unwrap_or_default(),
                        f.secondary_text.map(|t| t.text).unwrap_or_default(),
                    )
                })
                .unwrap_or_default();
            Suggestion {
                place_id: p.place_id,
                primary_text: primary,
                secondary_text: secondary,
                distance_meters: None,
            }
        })
        .collect())
}

pub struct PlaceCity {
    pub name: String,
    pub region: String,
}

/// Places API (New) `searchNearby` with `includedTypes: ["locality"]` around `(lat, lng)`.
pub async fn nearby_cities(
    http: &reqwest::Client,
    key: &str,
    lat: f64,
    lng: f64,
) -> Result<Vec<PlaceCity>, ApiError> {
    let body = json!({
        "includedTypes": ["locality"],
        "maxResultCount": 10,
        "locationRestriction": {
            "circle": {
                "center": { "latitude": lat, "longitude": lng },
                "radius": 50000.0
            }
        }
    });
    post_city_places(http, key, "places:searchNearby", body).await
}

/// Places API (New) `searchText` with `includedType: "locality"` for a country when GPS is unavailable.
pub async fn country_cities(
    http: &reqwest::Client,
    key: &str,
    country_name: &str,
    region_code: Option<&str>,
) -> Result<Vec<PlaceCity>, ApiError> {
    let mut body = json!({
        "textQuery": format!("major cities in {country_name}"),
        "includedType": "locality",
        "strictTypeFiltering": true,
        "pageSize": 10,
    });
    if let Some(region) = region_code {
        body["regionCode"] = json!(region.to_ascii_uppercase());
    }
    post_city_places(http, key, "places:searchText", body).await
}

async fn post_city_places(
    http: &reqwest::Client,
    key: &str,
    endpoint: &'static str,
    body: serde_json::Value,
) -> Result<Vec<PlaceCity>, ApiError> {
    let res = http
        .post(format!("{PLACES_BASE}/{endpoint}"))
        .timeout(TIMEOUT)
        .header("X-Goog-Api-Key", key)
        .header("X-Goog-FieldMask", "places.displayName,places.formattedAddress")
        .json(&body)
        .send()
        .await;
    let v = read_json(res, endpoint).await?;

    #[derive(Deserialize)]
    struct DisplayName {
        #[serde(default)]
        text: String,
    }
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Place {
        display_name: Option<DisplayName>,
        #[serde(default)]
        formatted_address: String,
    }
    #[derive(Deserialize)]
    struct Res {
        #[serde(default)]
        places: Vec<Place>,
    }
    let parsed: Res = serde_json::from_value(v).map_err(|e| bad_shape(endpoint, e))?;
    Ok(parsed
        .places
        .into_iter()
        .filter_map(|p| {
            let name = p.display_name?.text.trim().to_string();
            if name.is_empty() {
                return None;
            }
            Some(PlaceCity {
                name,
                region: p.formatted_address,
            })
        })
        .collect())
}

/// Place Details (New). Passing the autocomplete session token here closes the billing session,
/// which makes every autocomplete request in that session free (only this call is billed).
///
/// Cost trick: `displayName` is a *Pro* field and requesting it bills the whole call at Pro.
/// The app already shows the suggestion's `primary_text` as the name, so we ask only for
/// Essentials fields: id, formattedAddress, shortFormattedAddress, location.
pub async fn place_details(
    http: &reqwest::Client,
    key: &str,
    place_id: &str,
    session_token: Option<&str>,
) -> Result<PlaceDetails, ApiError> {
    let mut req = http
        .get(format!("{PLACES_BASE}/places/{place_id}"))
        .timeout(TIMEOUT)
        .header("X-Goog-Api-Key", key)
        .header("X-Goog-FieldMask", "id,formattedAddress,shortFormattedAddress,location");
    if let Some(token) = session_token {
        req = req.query(&[("sessionToken", token)]);
    }
    let v = read_json(req.send().await, "place details").await?;

    #[derive(Deserialize)]
    struct Loc {
        latitude: f64,
        longitude: f64,
    }
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Res {
        id: String,
        #[serde(default)]
        formatted_address: String,
        #[serde(default)]
        short_formatted_address: String,
        location: Loc,
    }
    let p: Res = serde_json::from_value(v).map_err(|e| bad_shape("place details", e))?;
    // "286 Mount Wellington Hwy, Mount Wellington" reads better as a fallback name than the full
    // address with postcode and country.
    let name = if p.short_formatted_address.is_empty() {
        p.formatted_address.split(',').next().unwrap_or_default().trim().to_string()
    } else {
        p.short_formatted_address
    };
    Ok(PlaceDetails {
        place_id: p.id,
        name,
        address: p.formatted_address,
        lat: p.location.latitude,
        lng: p.location.longitude,
    })
}

/// Reverse geocode a map pin to a street address (Geocoding API).
pub async fn reverse_geocode(
    http: &reqwest::Client,
    key: &str,
    at: LatLng,
) -> Result<Option<PlaceDetails>, ApiError> {
    let latlng = format!("{},{}", at.lat, at.lng);
    let res = http
        .get(GEOCODE_URL)
        .timeout(TIMEOUT)
        .query(&[("latlng", latlng.as_str()), ("key", key)])
        .send()
        .await;
    let v = read_json(res, "reverse geocode").await?;

    // The Geocoding API returns HTTP 200 with a status field.
    match v["status"].as_str() {
        Some("OK") => {}
        Some("ZERO_RESULTS") => return Ok(None),
        other => {
            tracing::warn!(status = ?other, error = ?v["error_message"], "google reverse geocode failed");
            return Err(ApiError::Unavailable("maps_failed"));
        }
    }
    // Results come nearest/most specific first. Skip Plus Code-only results ("7RQ2+X4 Ludhiana"):
    // riders and drivers recognise street addresses, not grid codes.
    let results = v["results"].as_array().cloned().unwrap_or_default();
    let is_plus_code = |r: &Value| {
        r["types"].as_array().is_some_and(|t| t.iter().any(|x| x == "plus_code"))
    };
    let Some(first) = results.iter().find(|r| !is_plus_code(r)).or(results.first()) else { return Ok(None) };
    let address = first["formatted_address"].as_str().unwrap_or_default().to_string();
    // "12 Ponsonby Rd, Grey Lynn, Auckland 1011, New Zealand" -> name "12 Ponsonby Rd".
    let name = address.split(',').next().unwrap_or_default().trim().to_string();
    Ok(Some(PlaceDetails {
        place_id: first["place_id"].as_str().unwrap_or_default().to_string(),
        name,
        address,
        lat: at.lat,
        lng: at.lng,
    }))
}

/// Place types (Places API Table A) that make recognisable pickup/drop-off points.
const LANDMARK_TYPES: &[&str] = &[
    "train_station", "bus_station", "transit_station", "subway_station", "airport",
    "shopping_mall", "supermarket", "grocery_store", "department_store",
    "hotel", "lodging", "restaurant", "cafe", "fast_food_restaurant",
    "hospital", "pharmacy", "university", "school", "park", "tourist_attraction", "museum",
    "gym", "bank", "movie_theater", "stadium", "church", "hindu_temple", "mosque",
    "gas_station", "parking", "library", "city_hall", "post_office",
];

/// Nearby Search (New): the closest named places around the rider, nearest first. Used as
/// zero-typing suggestions ("you're at Sylvia Park, pick the car park entrance").
///
/// Billing: every field here is on the Nearby Search *Pro* SKU (displayName/location have no
/// cheaper tier on this endpoint), so callers must keep this to one call per screen visit.
pub async fn nearby(
    http: &reqwest::Client,
    key: &str,
    at: LatLng,
    radius_m: f64,
    max_results: u32,
) -> Result<Vec<PlaceDetails>, ApiError> {
    let body = json!({
        "locationRestriction": { "circle": { "center": at.to_json(), "radius": radius_m } },
        "rankPreference": "DISTANCE",
        "maxResultCount": max_results,
        // Only places people use as pickup/drop-off landmarks. Without this, "nearest" is often a
        // home business or a PO-box listing nobody can walk to.
        "includedPrimaryTypes": LANDMARK_TYPES,
    });
    let res = http
        .post(format!("{PLACES_BASE}/places:searchNearby"))
        .timeout(TIMEOUT)
        .header("X-Goog-Api-Key", key)
        .header(
            "X-Goog-FieldMask",
            "places.id,places.displayName,places.formattedAddress,places.shortFormattedAddress,places.location",
        )
        .json(&body)
        .send()
        .await;
    let v = read_json(res, "nearby search").await?;

    #[derive(Deserialize)]
    struct Name {
        #[serde(default)]
        text: String,
    }
    #[derive(Deserialize)]
    struct Loc {
        latitude: f64,
        longitude: f64,
    }
    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct Place {
        id: String,
        display_name: Option<Name>,
        #[serde(default)]
        formatted_address: String,
        #[serde(default)]
        short_formatted_address: String,
        location: Loc,
    }
    #[derive(Deserialize)]
    struct Res {
        #[serde(default)]
        places: Vec<Place>,
    }
    let parsed: Res = serde_json::from_value(v).map_err(|e| bad_shape("nearby search", e))?;
    Ok(parsed
        .places
        .into_iter()
        .map(|p| PlaceDetails {
            place_id: p.id,
            name: p
                .display_name
                .map(|n| n.text)
                .filter(|n| !n.is_empty())
                .unwrap_or_else(|| p.short_formatted_address.clone()),
            address: if p.short_formatted_address.is_empty() { p.formatted_address } else { p.short_formatted_address },
            lat: p.location.latitude,
            lng: p.location.longitude,
        })
        .collect())
}

/// Routes API computeRoutes: driving, traffic-aware, optional intermediate stops.
pub async fn compute_route(
    http: &reqwest::Client,
    key: &str,
    origin: LatLng,
    stops: &[LatLng],
    destination: LatLng,
) -> Result<Route, ApiError> {
    let body = json!({
        "origin": origin.waypoint(),
        "destination": destination.waypoint(),
        "intermediates": stops.iter().map(|s| s.waypoint()).collect::<Vec<_>>(),
        "travelMode": "DRIVE",
        // Traffic-aware ETAs are what riders compare against; upfront prices depend on them.
        "routingPreference": "TRAFFIC_AWARE",
        "computeAlternativeRoutes": false,
        "units": "METRIC",
    });
    let res = http
        .post(ROUTES_URL)
        .timeout(TIMEOUT)
        .header("X-Goog-Api-Key", key)
        .header(
            "X-Goog-FieldMask",
            "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline",
        )
        .json(&body)
        .send()
        .await;
    let v = read_json(res, "compute routes").await?;

    let route = v["routes"].get(0).ok_or(ApiError::BadRequest("no_route"))?;
    // Duration is a protobuf Duration string such as "1234s".
    let duration_seconds = route["duration"]
        .as_str()
        .and_then(|d| d.trim_end_matches('s').parse::<f64>().ok())
        .map(|d| d.round() as i64)
        .unwrap_or(0);
    Ok(Route {
        distance_meters: route["distanceMeters"].as_i64().unwrap_or(0),
        duration_seconds,
        encoded_polyline: route["polyline"]["encodedPolyline"].as_str().unwrap_or_default().to_string(),
    })
}

/// Turn a Google response into JSON, logging the provider body on failure so the cause
/// (API not enabled, key restricted, quota exceeded) shows up in the server terminal.
async fn read_json(res: reqwest::Result<reqwest::Response>, what: &'static str) -> Result<Value, ApiError> {
    let res = res.map_err(|e| {
        tracing::warn!(error = %e, "google {what} request failed");
        ApiError::Unavailable("maps_failed")
    })?;
    let status = res.status();
    let text = res.text().await.unwrap_or_default();
    if !status.is_success() {
        tracing::warn!(%status, body = %text, "google {what} rejected request");
        return Err(if status.as_u16() == 429 {
            ApiError::TooManyRequests(60)
        } else {
            ApiError::Unavailable("maps_failed")
        });
    }
    serde_json::from_str(&text).map_err(|e| bad_shape(what, e))
}

fn bad_shape(what: &'static str, err: serde_json::Error) -> ApiError {
    tracing::error!(error = %err, "unexpected google {what} response");
    ApiError::Unavailable("maps_failed")
}
