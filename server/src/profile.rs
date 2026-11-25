//! Rider profile updates, saved places (Home/Work/custom), and emergency contacts.

use axum::{
    extract::{Path, State},
    http::{HeaderMap, StatusCode},
    routing::{delete, get, patch, post},
    Json, Router,
};
use chrono::{DateTime, Utc};
use garde::Validate;
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

use crate::{
    auth::{canonical_phone, jwt, load_user, require_user_id, validation_error, User},
    error::{is_unique_violation, ApiError},
    AppState,
};

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/me", patch(update_me))
        .route("/v1/me/verify-contact", post(verify_contact))
        .route("/v1/places", get(list_places).post(upsert_place))
        .route("/v1/places/{id}", delete(delete_place).patch(update_place))
        .route("/v1/emergency-contacts", get(list_contacts).post(create_contact))
        .route("/v1/emergency-contacts/{id}", delete(delete_contact))
}

#[derive(Deserialize, Validate)]
pub struct UpdateMeReq {
    #[garde(length(min = 1, max = 60))]
    first_name: String,
    #[garde(length(max = 60))]
    #[serde(default)]
    last_name: String,
}

async fn update_me(
    State(st): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<UpdateMeReq>,
) -> Result<Json<User>, ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    req.validate().map_err(validation_error)?;
    let first = req.first_name.trim().to_string();
    let last = req.last_name.trim().to_string();
    if first.is_empty() {
        return Err(ApiError::BadRequest("invalid_name"));
    }

    sqlx::query("UPDATE users SET first_name = $1, last_name = $2, updated_at = now() WHERE id = $3")
        .bind(&first)
        .bind(&last)
        .bind(user_id)
        .execute(&st.pool)
        .await?;

    Ok(Json(load_user(&st, user_id).await?))
}

#[derive(Deserialize)]
pub struct VerifyContactReq {
    otp_proof: String,
}

async fn verify_contact(
    State(st): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<VerifyContactReq>,
) -> Result<Json<User>, ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    let claims = jwt::decode_typed(&st.cfg.jwt_secret, &req.otp_proof, jwt::TYP_PROOF)?;
    let dest = claims.dest.ok_or(ApiError::Unauthorized)?;
    let res = match claims.channel.as_deref() {
        Some("phone") => {
            sqlx::query("UPDATE users SET phone_e164 = $1, phone_verified_at = now(), updated_at = now() WHERE id = $2")
                .bind(&dest)
                .bind(user_id)
                .execute(&st.pool)
                .await
        }
        Some("email") => {
            sqlx::query("UPDATE users SET email = $1, email_verified_at = now(), updated_at = now() WHERE id = $2")
                .bind(&dest)
                .bind(user_id)
                .execute(&st.pool)
                .await
        }
        _ => return Err(ApiError::BadRequest("proof_mismatch")),
    };
    match res {
        Ok(_) => {}
        Err(err) if is_unique_violation(&err) => return Err(ApiError::Conflict("already_registered")),
        Err(err) => return Err(err.into()),
    }
    Ok(Json(load_user(&st, user_id).await?))
}

// ---------- Saved places ----------

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum PlaceKind {
    Home,
    Work,
    Custom,
}

impl PlaceKind {
    fn as_str(self) -> &'static str {
        match self {
            PlaceKind::Home => "home",
            PlaceKind::Work => "work",
            PlaceKind::Custom => "custom",
        }
    }
}

#[derive(Debug, Serialize, FromRow)]
pub struct SavedPlace {
    pub id: Uuid,
    pub kind: String,
    pub label: String,
    pub address: String,
    pub lat: Option<f64>,
    pub lng: Option<f64>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Deserialize, Validate)]
pub struct UpsertPlaceReq {
    #[garde(skip)]
    kind: PlaceKind,
    #[garde(length(min = 1, max = 60))]
    label: String,
    #[garde(length(min = 2, max = 200))]
    address: String,
    #[garde(skip)]
    lat: Option<f64>,
    #[garde(skip)]
    lng: Option<f64>,
}

async fn list_places(
    State(st): State<AppState>,
    headers: HeaderMap,
) -> Result<Json<Vec<SavedPlace>>, ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    let rows = sqlx::query_as::<_, SavedPlace>(
        "SELECT id, kind, label, address, lat, lng, updated_at
         FROM saved_places
         WHERE user_id = $1
         ORDER BY CASE kind WHEN 'home' THEN 0 WHEN 'work' THEN 1 ELSE 2 END, created_at DESC",
    )
    .bind(user_id)
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(rows))
}

async fn upsert_place(
    State(st): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<UpsertPlaceReq>,
) -> Result<(StatusCode, Json<SavedPlace>), ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    req.validate().map_err(validation_error)?;
    let label = req.label.trim().to_string();
    let address = req.address.trim().to_string();
    if label.is_empty() || address.is_empty() {
        return Err(ApiError::BadRequest("invalid_request"));
    }

    let id = Uuid::now_v7();
    let kind = req.kind.as_str();
    let row = if req.kind == PlaceKind::Custom {
        sqlx::query_as::<_, SavedPlace>(
            "INSERT INTO saved_places (id, user_id, kind, label, address, lat, lng)
             VALUES ($1, $2, $3, $4, $5, $6, $7)
             RETURNING id, kind, label, address, lat, lng, updated_at",
        )
        .bind(id)
        .bind(user_id)
        .bind(kind)
        .bind(&label)
        .bind(&address)
        .bind(req.lat)
        .bind(req.lng)
        .fetch_one(&st.pool)
        .await?
    } else {
        sqlx::query_as::<_, SavedPlace>(
            "INSERT INTO saved_places (id, user_id, kind, label, address, lat, lng)
             VALUES ($1, $2, $3, $4, $5, $6, $7)
             ON CONFLICT (user_id, kind) WHERE kind IN ('home', 'work')
             DO UPDATE SET label = EXCLUDED.label, address = EXCLUDED.address,
                           lat = EXCLUDED.lat, lng = EXCLUDED.lng, updated_at = now()
             RETURNING id, kind, label, address, lat, lng, updated_at",
        )
        .bind(id)
        .bind(user_id)
        .bind(kind)
        .bind(&label)
        .bind(&address)
        .bind(req.lat)
        .bind(req.lng)
        .fetch_one(&st.pool)
        .await?
    };

    Ok((StatusCode::CREATED, Json(row)))
}

#[derive(Deserialize, Validate)]
pub struct UpdatePlaceReq {
    #[garde(length(min = 1, max = 60))]
    label: String,
    #[garde(length(min = 2, max = 200))]
    address: String,
    #[garde(skip)]
    lat: Option<f64>,
    #[garde(skip)]
    lng: Option<f64>,
}

/// Edit a saved place in place (label, address, coordinates). The kind (home/work/custom) is
/// fixed: switching kinds is delete + create, which keeps the one-home/one-work rule simple.
async fn update_place(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
    Json(req): Json<UpdatePlaceReq>,
) -> Result<Json<SavedPlace>, ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    req.validate().map_err(validation_error)?;
    let label = req.label.trim().to_string();
    let address = req.address.trim().to_string();
    if label.is_empty() || address.is_empty() {
        return Err(ApiError::BadRequest("invalid_request"));
    }
    let valid = |v: Option<f64>, lim: f64| v.is_none_or(|x| x.is_finite() && x.abs() <= lim);
    if !valid(req.lat, 90.0) || !valid(req.lng, 180.0) {
        return Err(ApiError::BadRequest("invalid_location"));
    }
    sqlx::query_as::<_, SavedPlace>(
        "UPDATE saved_places
         SET label = $1, address = $2, lat = $3, lng = $4, updated_at = now()
         WHERE id = $5 AND user_id = $6
         RETURNING id, kind, label, address, lat, lng, updated_at",
    )
    .bind(&label)
    .bind(&address)
    .bind(req.lat)
    .bind(req.lng)
    .bind(id)
    .bind(user_id)
    .fetch_optional(&st.pool)
    .await?
    .map(Json)
    .ok_or(ApiError::NotFound("place_not_found"))
}

async fn delete_place(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    let res = sqlx::query("DELETE FROM saved_places WHERE id = $1 AND user_id = $2")
        .bind(id)
        .bind(user_id)
        .execute(&st.pool)
        .await?;
    if res.rows_affected() == 0 {
        return Err(ApiError::NotFound("place_not_found"));
    }
    Ok(StatusCode::NO_CONTENT)
}

// ---------- Emergency contacts ----------

#[derive(Debug, Serialize, FromRow)]
pub struct EmergencyContact {
    pub id: Uuid,
    pub name: String,
    pub phone_e164: String,
    pub relationship: String,
}

#[derive(Deserialize, Validate)]
pub struct CreateContactReq {
    #[garde(length(min = 1, max = 60))]
    name: String,
    #[garde(length(min = 4, max = 32))]
    phone: String,
    #[garde(length(max = 40))]
    #[serde(default)]
    relationship: String,
}

async fn list_contacts(
    State(st): State<AppState>,
    headers: HeaderMap,
) -> Result<Json<Vec<EmergencyContact>>, ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    let rows = sqlx::query_as::<_, EmergencyContact>(
        "SELECT id, name, phone_e164, relationship
         FROM emergency_contacts
         WHERE user_id = $1
         ORDER BY created_at ASC",
    )
    .bind(user_id)
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(rows))
}

async fn create_contact(
    State(st): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<CreateContactReq>,
) -> Result<(StatusCode, Json<EmergencyContact>), ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    req.validate().map_err(validation_error)?;
    let name = req.name.trim().to_string();
    let relationship = req.relationship.trim().to_string();
    let phone_e164 = canonical_phone(&req.phone).ok_or(ApiError::BadRequest("invalid_phone"))?;
    if name.is_empty() {
        return Err(ApiError::BadRequest("invalid_name"));
    }

    let id = Uuid::now_v7();
    let row = sqlx::query_as::<_, EmergencyContact>(
        "INSERT INTO emergency_contacts (id, user_id, name, phone_e164, relationship)
         VALUES ($1, $2, $3, $4, $5)
         RETURNING id, name, phone_e164, relationship",
    )
    .bind(id)
    .bind(user_id)
    .bind(&name)
    .bind(&phone_e164)
    .bind(&relationship)
    .fetch_one(&st.pool)
    .await?;

    Ok((StatusCode::CREATED, Json(row)))
}

async fn delete_contact(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    let res = sqlx::query("DELETE FROM emergency_contacts WHERE id = $1 AND user_id = $2")
        .bind(id)
        .bind(user_id)
        .execute(&st.pool)
        .await?;
    if res.rows_affected() == 0 {
        return Err(ApiError::NotFound("contact_not_found"));
    }
    Ok(StatusCode::NO_CONTENT)
}
