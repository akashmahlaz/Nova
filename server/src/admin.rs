//! Admin-only API endpoints gated by `users.role = 'admin'`.

use axum::{
    extract::{Path, State},
    http::HeaderMap,
    routing::{get, patch},
    Json, Router,
};
use chrono::{DateTime, NaiveDate, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

use crate::{
    auth::{load_user, require_user_id, User},
    error::ApiError,
    AppState,
};

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/admin/stats", get(stats))
        .route("/v1/admin/overview", get(overview))
        .route("/v1/admin/users", get(list_users).post(create_user))
        .route("/v1/admin/users/{id}", patch(update_user).delete(delete_user))
        .route("/v1/admin/users/{id}/role", patch(update_role))
        .route("/v1/admin/drivers", get(list_drivers))
        .route("/v1/admin/drivers/{id}", patch(update_driver))
        .route("/v1/admin/rides", get(list_rides))
        .route("/v1/admin/service-areas", get(list_service_areas))
        .route("/v1/admin/service-areas/{id}", patch(update_service_area))
        .route("/v1/admin/media", get(list_media))
        .route("/v1/admin/media/{id}", patch(review_media).delete(delete_media))
}

async fn require_admin(st: &AppState, headers: &HeaderMap) -> Result<User, ApiError> {
    let user_id = require_user_id(st, headers)?;
    let user = load_user(st, user_id).await?;
    if user.role != "admin" {
        return Err(ApiError::Unauthorized);
    }
    Ok(user)
}

#[derive(Serialize, FromRow)]
pub struct AdminStats {
    pub total_users: i64,
    pub verified_phones: i64,
    pub verified_emails: i64,
    pub saved_places: i64,
    pub emergency_contacts: i64,
}

async fn stats(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<AdminStats>, ApiError> {
    require_admin(&st, &headers).await?;
    let row = sqlx::query_as::<_, AdminStats>(
        "SELECT
            (SELECT count(*) FROM users) AS total_users,
            (SELECT count(*) FROM users WHERE phone_verified_at IS NOT NULL) AS verified_phones,
            (SELECT count(*) FROM users WHERE email_verified_at IS NOT NULL) AS verified_emails,
            (SELECT count(*) FROM saved_places) AS saved_places,
            (SELECT count(*) FROM emergency_contacts) AS emergency_contacts",
    )
    .fetch_one(&st.pool)
    .await?;
    Ok(Json(row))
}

#[derive(Serialize, FromRow)]
struct OverviewCounts {
    total_users: i64,
    rider_users: i64,
    driver_users: i64,
    admin_users: i64,
    verified_phones: i64,
    verified_emails: i64,
    total_drivers: i64,
    pending_drivers: i64,
    approved_drivers: i64,
    suspended_drivers: i64,
    total_rides: i64,
    active_rides: i64,
    completed_rides: i64,
    cancelled_rides: i64,
    scheduled_rides: i64,
    gross_fare_cents: i64,
    active_service_areas: i64,
    total_service_areas: i64,
    saved_places: i64,
    emergency_contacts: i64,
    media_total: i64,
    media_pending_review: i64,
    media_verified: i64,
    media_bytes: i64,
}

#[derive(Serialize, FromRow)]
pub struct DailyRidePoint {
    day: String,
    total: i64,
    completed: i64,
    fare_cents: i64,
}

#[derive(Serialize)]
pub struct AdminOverview {
    total_users: i64,
    rider_users: i64,
    driver_users: i64,
    admin_users: i64,
    verified_phones: i64,
    verified_emails: i64,
    total_drivers: i64,
    pending_drivers: i64,
    approved_drivers: i64,
    suspended_drivers: i64,
    total_rides: i64,
    active_rides: i64,
    completed_rides: i64,
    cancelled_rides: i64,
    scheduled_rides: i64,
    gross_fare_cents: i64,
    active_service_areas: i64,
    total_service_areas: i64,
    saved_places: i64,
    emergency_contacts: i64,
    media_total: i64,
    media_pending_review: i64,
    media_verified: i64,
    media_bytes: i64,
    s3_configured: bool,
    s3_bucket: Option<String>,
    s3_region: Option<String>,
    daily_rides: Vec<DailyRidePoint>,
}

async fn overview(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<AdminOverview>, ApiError> {
    require_admin(&st, &headers).await?;
    let c = sqlx::query_as::<_, OverviewCounts>(
        "SELECT
            (SELECT count(*) FROM users) AS total_users,
            (SELECT count(*) FROM users WHERE role = 'rider') AS rider_users,
            (SELECT count(*) FROM users WHERE role = 'driver') AS driver_users,
            (SELECT count(*) FROM users WHERE role = 'admin') AS admin_users,
            (SELECT count(*) FROM users WHERE phone_verified_at IS NOT NULL) AS verified_phones,
            (SELECT count(*) FROM users WHERE email_verified_at IS NOT NULL) AS verified_emails,
            (SELECT count(*) FROM drivers) AS total_drivers,
            (SELECT count(*) FROM drivers WHERE status = 'pending') AS pending_drivers,
            (SELECT count(*) FROM drivers WHERE status = 'approved') AS approved_drivers,
            (SELECT count(*) FROM drivers WHERE status = 'suspended') AS suspended_drivers,
            (SELECT count(*) FROM rides) AS total_rides,
            (SELECT count(*) FROM rides WHERE status IN ('requested', 'accepted', 'arriving', 'in_progress')) AS active_rides,
            (SELECT count(*) FROM rides WHERE status = 'completed') AS completed_rides,
            (SELECT count(*) FROM rides WHERE status IN ('cancelled', 'no_drivers')) AS cancelled_rides,
            (SELECT count(*) FROM rides WHERE status = 'scheduled') AS scheduled_rides,
            (SELECT COALESCE(SUM(fare_cents), 0)::BIGINT FROM rides WHERE status = 'completed') AS gross_fare_cents,
            (SELECT count(*) FROM service_areas WHERE active) AS active_service_areas,
            (SELECT count(*) FROM service_areas) AS total_service_areas,
            (SELECT count(*) FROM saved_places) AS saved_places,
            (SELECT count(*) FROM emergency_contacts) AS emergency_contacts,
            (SELECT count(*) FROM media_objects WHERE status <> 'pending') AS media_total,
            (SELECT count(*) FROM media_objects WHERE status = 'uploaded') AS media_pending_review,
            (SELECT count(*) FROM media_objects WHERE status = 'verified') AS media_verified,
            (SELECT COALESCE(SUM(size_bytes), 0)::BIGINT FROM media_objects WHERE status <> 'pending') AS media_bytes",
    )
    .fetch_one(&st.pool)
    .await?;

    let daily_rides = sqlx::query_as::<_, DailyRidePoint>(
        "SELECT
            to_char(d::date, 'Mon DD') AS day,
            COALESCE(count(r.id), 0) AS total,
            COALESCE(count(r.id) FILTER (WHERE r.status = 'completed'), 0) AS completed,
            COALESCE(SUM(r.fare_cents) FILTER (WHERE r.status = 'completed'), 0)::BIGINT AS fare_cents
         FROM generate_series(CURRENT_DATE - INTERVAL '6 days', CURRENT_DATE, INTERVAL '1 day') AS d
         LEFT JOIN rides r ON r.created_at::date = d::date
         GROUP BY d
         ORDER BY d ASC",
    )
    .fetch_all(&st.pool)
    .await?;

    Ok(Json(AdminOverview {
        total_users: c.total_users,
        rider_users: c.rider_users,
        driver_users: c.driver_users,
        admin_users: c.admin_users,
        verified_phones: c.verified_phones,
        verified_emails: c.verified_emails,
        total_drivers: c.total_drivers,
        pending_drivers: c.pending_drivers,
        approved_drivers: c.approved_drivers,
        suspended_drivers: c.suspended_drivers,
        total_rides: c.total_rides,
        active_rides: c.active_rides,
        completed_rides: c.completed_rides,
        cancelled_rides: c.cancelled_rides,
        scheduled_rides: c.scheduled_rides,
        gross_fare_cents: c.gross_fare_cents,
        active_service_areas: c.active_service_areas,
        total_service_areas: c.total_service_areas,
        saved_places: c.saved_places,
        emergency_contacts: c.emergency_contacts,
        media_total: c.media_total,
        media_pending_review: c.media_pending_review,
        media_verified: c.media_verified,
        media_bytes: c.media_bytes,
        s3_configured: st.cfg.s3.is_some(),
        s3_bucket: st.cfg.s3.as_ref().map(|s| s.bucket.clone()),
        s3_region: st.cfg.s3.as_ref().map(|s| s.region.clone()),
        daily_rides,
    }))
}

async fn list_users(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<Vec<User>>, ApiError> {
    require_admin(&st, &headers).await?;
    let rows = sqlx::query_as::<_, User>(
        "SELECT id, phone_e164, email, first_name, last_name, role, phone_verified_at, email_verified_at, created_at
         FROM users
         ORDER BY created_at DESC
         LIMIT 200",
    )
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(rows))
}

#[derive(Deserialize)]
pub struct UpdateRoleReq {
    role: String,
}

async fn update_role(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
    Json(req): Json<UpdateRoleReq>,
) -> Result<Json<User>, ApiError> {
    require_admin(&st, &headers).await?;
    if !matches!(req.role.as_str(), "rider" | "driver" | "admin") {
        return Err(ApiError::BadRequest("invalid_role"));
    }
    sqlx::query("UPDATE users SET role = $1, updated_at = now() WHERE id = $2")
        .bind(&req.role)
        .bind(id)
        .execute(&st.pool)
        .await?;
    Ok(Json(load_user(&st, id).await?))
}

#[derive(Deserialize)]
pub struct CreateUserReq {
    first_name: String,
    last_name: String,
    email: Option<String>,
    phone_e164: Option<String>,
    role: String,
}

async fn create_user(
    State(st): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<CreateUserReq>,
) -> Result<Json<User>, ApiError> {
    let admin = require_admin(&st, &headers).await?;
    if !matches!(req.role.as_str(), "rider" | "driver" | "admin") {
        return Err(ApiError::BadRequest("invalid_role"));
    }
    let first = req.first_name.trim();
    let last = req.last_name.trim();
    if first.is_empty() {
        return Err(ApiError::BadRequest("invalid_name"));
    }
    let email = req.email.as_deref().map(str::trim).filter(|s| !s.is_empty()).map(str::to_lowercase);
    let phone = req.phone_e164.as_deref().map(str::trim).filter(|s| !s.is_empty()).map(str::to_string);
    if email.is_none() && phone.is_none() {
        return Err(ApiError::BadRequest("contact_required"));
    }

    let id = Uuid::now_v7();
    let res = sqlx::query(
        "INSERT INTO users (id, first_name, last_name, email, phone_e164, role, email_verified_at, phone_verified_at)
         VALUES ($1, $2, $3, $4, $5, $6,
                 CASE WHEN $4 IS NOT NULL THEN now() ELSE NULL END,
                 CASE WHEN $5 IS NOT NULL THEN now() ELSE NULL END)",
    )
    .bind(id)
    .bind(first)
    .bind(last)
    .bind(&email)
    .bind(&phone)
    .bind(&req.role)
    .execute(&st.pool)
    .await;

    if let Err(err) = res {
        if crate::error::is_unique_violation(&err) {
            return Err(ApiError::Conflict("user_already_exists"));
        }
        return Err(err.into());
    }
    tracing::info!(admin = %admin.id, created_user = %id, role = %req.role, "admin created user");
    Ok(Json(load_user(&st, id).await?))
}

#[derive(Deserialize)]
pub struct UpdateUserReq {
    first_name: Option<String>,
    last_name: Option<String>,
    email: Option<String>,
    phone_e164: Option<String>,
    role: Option<String>,
    verify_email: Option<bool>,
    verify_phone: Option<bool>,
}

async fn update_user(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
    Json(req): Json<UpdateUserReq>,
) -> Result<Json<User>, ApiError> {
    let admin = require_admin(&st, &headers).await?;
    if let Some(ref role) = req.role {
        if !matches!(role.as_str(), "rider" | "driver" | "admin") {
            return Err(ApiError::BadRequest("invalid_role"));
        }
    }
    let email = req.email.as_deref().map(str::trim).map(str::to_lowercase);
    let phone = req.phone_e164.as_deref().map(str::trim).map(str::to_string);

    let res = sqlx::query(
        "UPDATE users SET
            first_name = COALESCE(NULLIF($1, ''), first_name),
            last_name = COALESCE($2, last_name),
            email = CASE WHEN $3::text IS NULL THEN email ELSE NULLIF($3, '') END,
            phone_e164 = CASE WHEN $4::text IS NULL THEN phone_e164 ELSE NULLIF($4, '') END,
            role = COALESCE($5, role),
            email_verified_at = CASE WHEN $6 IS TRUE THEN COALESCE(email_verified_at, now()) WHEN $6 IS FALSE THEN NULL ELSE email_verified_at END,
            phone_verified_at = CASE WHEN $7 IS TRUE THEN COALESCE(phone_verified_at, now()) WHEN $7 IS FALSE THEN NULL ELSE phone_verified_at END,
            updated_at = now()
         WHERE id = $8",
    )
    .bind(req.first_name.as_deref().map(str::trim))
    .bind(req.last_name.as_deref().map(str::trim))
    .bind(email)
    .bind(phone)
    .bind(req.role.as_deref())
    .bind(req.verify_email)
    .bind(req.verify_phone)
    .bind(id)
    .execute(&st.pool)
    .await;

    match res {
        Ok(done) if done.rows_affected() == 0 => return Err(ApiError::NotFound("user_not_found")),
        Err(err) if crate::error::is_unique_violation(&err) => {
            return Err(ApiError::Conflict("contact_already_in_use"));
        }
        Err(err) => return Err(err.into()),
        _ => {}
    }

    tracing::info!(admin = %admin.id, user = %id, "admin updated user");
    Ok(Json(load_user(&st, id).await?))
}

async fn delete_user(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
) -> Result<axum::http::StatusCode, ApiError> {
    let admin = require_admin(&st, &headers).await?;
    if admin.id == id {
        return Err(ApiError::BadRequest("cannot_delete_self"));
    }
    let done = sqlx::query("DELETE FROM users WHERE id = $1")
        .bind(id)
        .execute(&st.pool)
        .await?;
    if done.rows_affected() == 0 {
        return Err(ApiError::NotFound("user_not_found"));
    }
    tracing::info!(admin = %admin.id, deleted_user = %id, "admin deleted user");
    Ok(axum::http::StatusCode::NO_CONTENT)
}

// ---------- drivers ----------

#[derive(Serialize, FromRow)]
pub struct AdminDriver {
    user_id: Uuid,
    first_name: String,
    last_name: String,
    phone_e164: Option<String>,
    status: String,
    licence_number: String,
    licence_expiry: Option<NaiveDate>,
    vehicle: Option<String>,
    created_at: DateTime<Utc>,
}

/// Applications first (pending), then everyone else, newest first.
async fn list_drivers(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<Vec<AdminDriver>>, ApiError> {
    require_admin(&st, &headers).await?;
    let rows = sqlx::query_as::<_, AdminDriver>(
        "SELECT d.user_id, u.first_name, u.last_name, u.phone_e164, d.status, d.licence_number, d.licence_expiry,
                concat_ws(' ', v.year, v.color, v.make, v.model) || ' · ' || v.plate AS vehicle, d.created_at
         FROM drivers d
         JOIN users u ON u.id = d.user_id
         LEFT JOIN vehicles v ON v.driver_id = d.user_id AND v.active
         ORDER BY (d.status = 'pending') DESC, d.created_at DESC
         LIMIT 200",
    )
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(rows))
}

#[derive(Deserialize)]
pub struct UpdateDriverReq {
    status: String,
}

async fn update_driver(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
    Json(req): Json<UpdateDriverReq>,
) -> Result<axum::http::StatusCode, ApiError> {
    let admin = require_admin(&st, &headers).await?;
    if !matches!(req.status.as_str(), "pending" | "approved" | "suspended") {
        return Err(ApiError::BadRequest("invalid_status"));
    }
    let done = sqlx::query(
        "UPDATE drivers SET status = $1, updated_at = now(),
                approved_at = CASE WHEN $1 = 'approved' THEN COALESCE(approved_at, now()) ELSE approved_at END
         WHERE user_id = $2",
    )
    .bind(&req.status)
    .bind(id)
    .execute(&st.pool)
    .await?;
    if done.rows_affected() == 0 {
        return Err(ApiError::NotFound("driver_not_found"));
    }
    if req.status != "approved" {
        // A suspended driver drops out of dispatch at once.
        let mut redis = st.redis.clone();
        let _: () = redis::pipe()
            .cmd("ZREM").arg(crate::drivers::ONLINE_KEY).arg(id.to_string()).ignore()
            .cmd("DEL").arg(crate::drivers::seen_key(id)).ignore()
            .query_async(&mut redis)
            .await?;
    }
    tracing::info!(admin = %admin.id, driver = %id, status = %req.status, "driver status changed");
    Ok(axum::http::StatusCode::NO_CONTENT)
}

// ---------- rides ----------

#[derive(Serialize, FromRow)]
pub struct AdminRide {
    id: Uuid,
    status: String,
    product_id: String,
    service_area_name: String,
    rider_name: String,
    driver_name: Option<String>,
    pickup_name: String,
    dropoff_name: String,
    currency: String,
    fare_cents: i64,
    distance_meters: i64,
    duration_seconds: i64,
    payment_method: String,
    created_at: DateTime<Utc>,
}

async fn list_rides(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<Vec<AdminRide>>, ApiError> {
    require_admin(&st, &headers).await?;
    let rows = sqlx::query_as::<_, AdminRide>(
        "SELECT r.id, r.status, r.product_id, sa.name AS service_area_name,
                trim(concat(ru.first_name, ' ', ru.last_name)) AS rider_name,
                CASE WHEN du.id IS NOT NULL THEN trim(concat(du.first_name, ' ', du.last_name)) ELSE NULL END AS driver_name,
                r.pickup_name, r.dropoff_name, r.currency, r.fare_cents, r.distance_meters, r.duration_seconds,
                r.payment_method, r.created_at
         FROM rides r
         JOIN users ru ON ru.id = r.rider_id
         JOIN service_areas sa ON sa.id = r.service_area_id
         LEFT JOIN users du ON du.id = r.driver_id
         ORDER BY r.created_at DESC
         LIMIT 120",
    )
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(rows))
}

// ---------- service areas ----------

#[derive(Serialize, FromRow)]
pub struct AdminServiceArea {
    id: Uuid,
    name: String,
    region_code: String,
    currency: String,
    timezone: String,
    active: bool,
    rate_count: i64,
    ride_count: i64,
    created_at: DateTime<Utc>,
}

async fn list_service_areas(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<Vec<AdminServiceArea>>, ApiError> {
    require_admin(&st, &headers).await?;
    let rows = sqlx::query_as::<_, AdminServiceArea>(
        "SELECT sa.id, sa.name, sa.region_code, sa.currency, sa.timezone, sa.active,
                (SELECT count(*) FROM fare_rates fr WHERE fr.service_area_id = sa.id AND fr.active) AS rate_count,
                (SELECT count(*) FROM rides r WHERE r.service_area_id = sa.id) AS ride_count,
                sa.created_at
         FROM service_areas sa
         ORDER BY sa.name ASC",
    )
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(rows))
}

#[derive(Deserialize)]
pub struct UpdateServiceAreaReq {
    active: bool,
}

async fn update_service_area(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
    Json(req): Json<UpdateServiceAreaReq>,
) -> Result<axum::http::StatusCode, ApiError> {
    require_admin(&st, &headers).await?;
    let done = sqlx::query("UPDATE service_areas SET active = $1, updated_at = now() WHERE id = $2")
        .bind(req.active)
        .bind(id)
        .execute(&st.pool)
        .await?;
    if done.rows_affected() == 0 {
        return Err(ApiError::NotFound("service_area_not_found"));
    }
    Ok(axum::http::StatusCode::NO_CONTENT)
}

// ---------- S3 media vault ----------

#[derive(FromRow)]
struct AdminMediaRow {
    id: Uuid,
    owner_user_id: Uuid,
    owner_name: String,
    owner_role: String,
    bucket: String,
    object_key: String,
    category: String,
    file_name: String,
    content_type: String,
    size_bytes: i64,
    status: String,
    rejection_reason: Option<String>,
    created_at: DateTime<Utc>,
}

#[derive(Serialize)]
pub struct AdminMediaItem {
    id: Uuid,
    owner_user_id: Uuid,
    owner_name: String,
    owner_role: String,
    bucket: String,
    object_key: String,
    category: String,
    file_name: String,
    content_type: String,
    size_bytes: i64,
    status: String,
    rejection_reason: Option<String>,
    view_url: Option<String>,
    created_at: DateTime<Utc>,
}

async fn list_media(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<Vec<AdminMediaItem>>, ApiError> {
    require_admin(&st, &headers).await?;
    let rows = sqlx::query_as::<_, AdminMediaRow>(
        "SELECT m.id, m.owner_user_id,
                trim(concat(u.first_name, ' ', u.last_name)) AS owner_name,
                u.role AS owner_role,
                m.bucket, m.object_key, m.category, m.file_name, m.content_type,
                m.size_bytes, m.status, m.rejection_reason, m.created_at
         FROM media_objects m
         JOIN users u ON u.id = m.owner_user_id
         ORDER BY (m.status = 'uploaded') DESC, m.created_at DESC
         LIMIT 200",
    )
    .fetch_all(&st.pool)
    .await?;

    let items = rows
        .into_iter()
        .map(|r| {
            let view_url = if r.status != "pending" {
                st.cfg.s3.as_ref().map(|s3| s3.presign_get(&r.object_key, 900))
            } else {
                None
            };
            AdminMediaItem {
                id: r.id,
                owner_user_id: r.owner_user_id,
                owner_name: r.owner_name,
                owner_role: r.owner_role,
                bucket: r.bucket,
                object_key: r.object_key,
                category: r.category,
                file_name: r.file_name,
                content_type: r.content_type,
                size_bytes: r.size_bytes,
                status: r.status,
                rejection_reason: r.rejection_reason,
                view_url,
                created_at: r.created_at,
            }
        })
        .collect();

    Ok(Json(items))
}

#[derive(Deserialize)]
pub struct ReviewMediaReq {
    status: String,
    rejection_reason: Option<String>,
}

async fn review_media(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
    Json(req): Json<ReviewMediaReq>,
) -> Result<axum::http::StatusCode, ApiError> {
    let admin = require_admin(&st, &headers).await?;
    if !matches!(req.status.as_str(), "verified" | "rejected") {
        return Err(ApiError::BadRequest("invalid_status"));
    }
    let done = sqlx::query(
        "UPDATE media_objects
         SET status = $1, reviewed_by = $2, reviewed_at = now(), rejection_reason = $3, updated_at = now()
         WHERE id = $4",
    )
    .bind(&req.status)
    .bind(admin.id)
    .bind(req.rejection_reason.as_deref())
    .bind(id)
    .execute(&st.pool)
    .await?;
    if done.rows_affected() == 0 {
        return Err(ApiError::NotFound("media_not_found"));
    }
    Ok(axum::http::StatusCode::NO_CONTENT)
}

async fn delete_media(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
) -> Result<axum::http::StatusCode, ApiError> {
    require_admin(&st, &headers).await?;
    let key: Option<String> = sqlx::query_scalar("DELETE FROM media_objects WHERE id = $1 RETURNING object_key")
        .bind(id)
        .fetch_optional(&st.pool)
        .await?;
    let key = key.ok_or(ApiError::NotFound("media_not_found"))?;
    if let Some(s3) = &st.cfg.s3 {
        if let Err(err) = s3.delete_object(&st.http, &key).await {
            tracing::warn!(error = %err, key = %key, "s3 delete failed after db delete");
        }
    }
    Ok(axum::http::StatusCode::NO_CONTENT)
}
