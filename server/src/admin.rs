//! Admin-only API endpoints gated by `users.role = 'admin'`.

use axum::{
    extract::{Path, State},
    http::HeaderMap,
    extract::Query,
    routing::{get, patch, post},
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
        // No DELETE: accounts are deactivated so rides, documents and payouts keep their history.
        .route("/v1/admin/users/{id}", patch(update_user))
        .route("/v1/admin/users/{id}/role", patch(update_role))
        .route("/v1/admin/users/{id}/deactivate", post(deactivate_user))
        .route("/v1/admin/users/{id}/reactivate", post(reactivate_user))
        .route("/v1/admin/audit", get(list_audit))
        .route("/v1/admin/drivers", get(list_drivers))
        .route("/v1/admin/drivers/{id}", get(driver_detail).patch(update_driver))
        .route("/v1/admin/rides", get(list_rides))
        .route("/v1/admin/rides/{id}", get(ride_detail))
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

// ---------- audit trail ----------

/// Records an admin action. The action itself already happened, so a failed write is logged
/// loudly rather than undoing it.
async fn audit(st: &AppState, admin: Uuid, action: &str, target_type: &str, target_id: Uuid, summary: &str, reason: Option<&str>) {
    let res = sqlx::query(
        "INSERT INTO admin_audit_log (id, admin_id, action, target_type, target_id, summary, reason)
         VALUES ($1, $2, $3, $4, $5, $6, $7)",
    )
    .bind(Uuid::now_v7())
    .bind(admin)
    .bind(action)
    .bind(target_type)
    .bind(target_id)
    .bind(summary)
    .bind(reason)
    .execute(&st.pool)
    .await;
    if let Err(err) = res {
        tracing::error!(error = ?err, %admin, action, %target_id, "audit write failed");
    }
}

async fn display_name(st: &AppState, id: Uuid) -> String {
    sqlx::query_scalar::<_, String>("SELECT trim(concat(first_name, ' ', last_name)) FROM users WHERE id = $1")
        .bind(id)
        .fetch_optional(&st.pool)
        .await
        .ok()
        .flatten()
        .unwrap_or_else(|| "Unknown account".into())
}

/// A reason an admin must give, trimmed and capped.
fn required_reason(reason: Option<&str>) -> Result<String, ApiError> {
    let r = reason.map(str::trim).unwrap_or("");
    if r.chars().count() < 3 {
        return Err(ApiError::BadRequest("reason_required"));
    }
    Ok(r.chars().take(500).collect())
}

#[derive(Serialize, FromRow)]
pub struct AuditEntry {
    id: Uuid,
    admin_id: Uuid,
    admin_name: String,
    action: String,
    target_type: String,
    target_id: Uuid,
    summary: String,
    reason: Option<String>,
    created_at: DateTime<Utc>,
}

#[derive(Deserialize)]
pub struct AuditQuery {
    target_type: Option<String>,
    target_id: Option<Uuid>,
    limit: Option<i64>,
}

async fn list_audit(State(st): State<AppState>, headers: HeaderMap, Query(q): Query<AuditQuery>) -> Result<Json<Vec<AuditEntry>>, ApiError> {
    require_admin(&st, &headers).await?;
    let rows = sqlx::query_as::<_, AuditEntry>(
        "SELECT a.id, a.admin_id, trim(concat(u.first_name, ' ', u.last_name)) AS admin_name, a.action,
                a.target_type, a.target_id, a.summary, a.reason, a.created_at
         FROM admin_audit_log a JOIN users u ON u.id = a.admin_id
         WHERE ($1::text IS NULL OR a.target_type = $1) AND ($2::uuid IS NULL OR a.target_id = $2)
         ORDER BY a.created_at DESC
         LIMIT $3",
    )
    .bind(q.target_type)
    .bind(q.target_id)
    .bind(q.limit.unwrap_or(100).clamp(1, 500))
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(rows))
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
    date: String,
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
    /// Drivers with a live heartbeat in dispatch right now (Redis).
    online_drivers: i64,
    /// Last 14 days, oldest first, so the console can compare this week with the one before.
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
            to_char(d::date, 'YYYY-MM-DD') AS date,
            to_char(d::date, 'Mon DD') AS day,
            COALESCE(count(r.id), 0) AS total,
            COALESCE(count(r.id) FILTER (WHERE r.status = 'completed'), 0) AS completed,
            COALESCE(SUM(r.fare_cents) FILTER (WHERE r.status = 'completed'), 0)::BIGINT AS fare_cents
         FROM generate_series(CURRENT_DATE - INTERVAL '13 days', CURRENT_DATE, INTERVAL '1 day') AS d
         LEFT JOIN rides r ON r.created_at::date = d::date
         GROUP BY d
         ORDER BY d ASC",
    )
    .fetch_all(&st.pool)
    .await?;
    let mut redis = st.redis.clone();
    let online_drivers: i64 = redis::cmd("ZCARD").arg(crate::drivers::ONLINE_KEY).query_async(&mut redis).await.unwrap_or(0);

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
        online_drivers,
        daily_rides,
    }))
}

#[derive(Serialize, FromRow)]
pub struct AdminUser {
    id: Uuid,
    phone_e164: Option<String>,
    email: Option<String>,
    first_name: String,
    last_name: String,
    role: String,
    phone_verified_at: Option<DateTime<Utc>>,
    email_verified_at: Option<DateTime<Utc>>,
    created_at: DateTime<Utc>,
    deactivated_at: Option<DateTime<Utc>>,
    deactivated_reason: Option<String>,
    /// Rides booked as a rider.
    rides_count: i64,
    /// Has a driver application (any status).
    has_driver_profile: bool,
}

async fn list_users(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<Vec<AdminUser>>, ApiError> {
    require_admin(&st, &headers).await?;
    let rows = sqlx::query_as::<_, AdminUser>(
        "SELECT u.id, u.phone_e164, u.email, u.first_name, u.last_name, u.role, u.phone_verified_at, u.email_verified_at,
                u.created_at, u.deactivated_at, u.deactivated_reason,
                (SELECT count(*) FROM rides r WHERE r.rider_id = u.id) AS rides_count,
                EXISTS (SELECT 1 FROM drivers d WHERE d.user_id = u.id) AS has_driver_profile
         FROM users u
         ORDER BY u.created_at DESC
         LIMIT 500",
    )
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(rows))
}

#[derive(Deserialize)]
pub struct UpdateRoleReq {
    role: String,
}

/// One account, one role, and the console can never lock everyone out.
async fn check_role_change(st: &AppState, admin: &User, id: Uuid, role: &str) -> Result<Option<String>, ApiError> {
    if !matches!(role, "rider" | "driver" | "admin") {
        return Err(ApiError::BadRequest("invalid_role"));
    }
    let current: Option<String> = sqlx::query_scalar("SELECT role FROM users WHERE id = $1").bind(id).fetch_optional(&st.pool).await?;
    let current = current.ok_or(ApiError::NotFound("user_not_found"))?;
    if current == role {
        return Ok(None);
    }
    if admin.id == id {
        return Err(ApiError::Conflict("cannot_change_own_role"));
    }
    if current == "admin" {
        let admins: i64 = sqlx::query_scalar("SELECT count(*) FROM users WHERE role = 'admin' AND deactivated_at IS NULL").fetch_one(&st.pool).await?;
        if admins <= 1 {
            return Err(ApiError::Conflict("last_admin"));
        }
    }
    let has_driver: bool = sqlx::query_scalar("SELECT EXISTS (SELECT 1 FROM drivers WHERE user_id = $1)").bind(id).fetch_one(&st.pool).await?;
    if has_driver && role != "driver" {
        return Err(ApiError::Conflict("driver_profile_exists"));
    }
    Ok(Some(current))
}

async fn update_role(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
    Json(req): Json<UpdateRoleReq>,
) -> Result<Json<User>, ApiError> {
    let admin = require_admin(&st, &headers).await?;
    if let Some(from) = check_role_change(&st, &admin, id, &req.role).await? {
        sqlx::query("UPDATE users SET role = $1, updated_at = now() WHERE id = $2")
            .bind(&req.role)
            .bind(id)
            .execute(&st.pool)
            .await?;
        if req.role != "admin" || from == "admin" {
            // A role change ends existing sessions so the new permissions apply at once.
            sqlx::query("UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL").bind(id).execute(&st.pool).await?;
        }
        let name = display_name(&st, id).await;
        audit(&st, admin.id, "user.role_changed", "user", id, &format!("Changed {name} from {from} to {}", req.role), None).await;
    }
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
    audit(&st, admin.id, "user.created", "user", id, &format!("Created {} {} as {}", first, last, req.role).replace("  ", " "), None).await;
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
    let mut role_from: Option<String> = None;
    if let Some(ref role) = req.role {
        role_from = check_role_change(&st, &admin, id, role).await?;
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
    let name = display_name(&st, id).await;
    let summary = match (&role_from, &req.role) {
        (Some(from), Some(to)) => format!("Edited {name}'s profile and changed role from {from} to {to}"),
        _ => format!("Edited {name}'s profile"),
    };
    audit(&st, admin.id, "user.updated", "user", id, &summary, None).await;
    Ok(Json(load_user(&st, id).await?))
}

#[derive(Deserialize)]
pub struct ReasonReq {
    reason: Option<String>,
}

/// Signs the account out everywhere and blocks sign-in. Rides, documents and payouts stay.
async fn deactivate_user(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
    Json(req): Json<ReasonReq>,
) -> Result<axum::http::StatusCode, ApiError> {
    let admin = require_admin(&st, &headers).await?;
    let reason = required_reason(req.reason.as_deref())?;
    if admin.id == id {
        return Err(ApiError::Conflict("cannot_deactivate_self"));
    }
    let role: Option<String> = sqlx::query_scalar("SELECT role FROM users WHERE id = $1 AND deactivated_at IS NULL")
        .bind(id)
        .fetch_optional(&st.pool)
        .await?;
    let role = role.ok_or(ApiError::NotFound("user_not_found"))?;
    if role == "admin" {
        let admins: i64 = sqlx::query_scalar("SELECT count(*) FROM users WHERE role = 'admin' AND deactivated_at IS NULL").fetch_one(&st.pool).await?;
        if admins <= 1 {
            return Err(ApiError::Conflict("last_admin"));
        }
    }
    let mut tx = st.pool.begin().await?;
    sqlx::query("UPDATE users SET deactivated_at = now(), deactivated_reason = $2, updated_at = now() WHERE id = $1")
        .bind(id)
        .bind(&reason)
        .execute(&mut *tx)
        .await?;
    sqlx::query("UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL")
        .bind(id)
        .execute(&mut *tx)
        .await?;
    // A driver stops getting trips straight away.
    sqlx::query("UPDATE drivers SET status = 'suspended', review_note = $2, updated_at = now() WHERE user_id = $1 AND status <> 'suspended'")
        .bind(id)
        .bind(&reason)
        .execute(&mut *tx)
        .await?;
    tx.commit().await?;
    let mut redis = st.redis.clone();
    let _: Result<(), _> = redis::pipe()
        .cmd("ZREM").arg(crate::drivers::ONLINE_KEY).arg(id.to_string()).ignore()
        .cmd("DEL").arg(crate::drivers::seen_key(id)).ignore()
        .query_async::<()>(&mut redis)
        .await;
    st.hub.send(id, serde_json::json!({ "type": "driver_status", "status": "suspended" }));
    let name = display_name(&st, id).await;
    audit(&st, admin.id, "user.deactivated", "user", id, &format!("Deactivated {name}"), Some(&reason)).await;
    Ok(axum::http::StatusCode::NO_CONTENT)
}

/// Lets the account sign in again. A driver stays suspended until reviewed in Drivers.
async fn reactivate_user(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
    Json(req): Json<ReasonReq>,
) -> Result<axum::http::StatusCode, ApiError> {
    let admin = require_admin(&st, &headers).await?;
    let reason = required_reason(req.reason.as_deref())?;
    let done = sqlx::query("UPDATE users SET deactivated_at = NULL, deactivated_reason = NULL, updated_at = now() WHERE id = $1 AND deactivated_at IS NOT NULL")
        .bind(id)
        .execute(&st.pool)
        .await?;
    if done.rows_affected() == 0 {
        return Err(ApiError::NotFound("user_not_found"));
    }
    let name = display_name(&st, id).await;
    audit(&st, admin.id, "user.reactivated", "user", id, &format!("Reactivated {name}"), Some(&reason)).await;
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
    submitted_at: Option<DateTime<Utc>>,
    /// Required documents uploaded or verified, out of `docs_required`.
    docs_ready: i64,
    docs_required: i64,
    created_at: DateTime<Utc>,
}

/// Applications first (pending), then everyone else, newest first.
async fn list_drivers(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<Vec<AdminDriver>>, ApiError> {
    require_admin(&st, &headers).await?;
    let rows = sqlx::query_as::<_, AdminDriver>(
        "SELECT d.user_id, u.first_name, u.last_name, u.phone_e164, d.status, d.licence_number, d.licence_expiry,
                concat_ws(' ', v.year, v.color, v.make, v.model) || ' · ' || v.plate AS vehicle, d.submitted_at,
                (SELECT count(*) FROM driver_document_types t WHERE t.required AND EXISTS (
                    SELECT 1 FROM media_objects m WHERE m.owner_user_id = d.user_id AND m.category = t.category
                      AND m.status IN ('uploaded', 'verified'))) AS docs_ready,
                (SELECT count(*) FROM driver_document_types WHERE required) AS docs_required,
                d.created_at
         FROM drivers d
         JOIN users u ON u.id = d.user_id
         LEFT JOIN vehicles v ON v.driver_id = d.user_id AND v.active
         ORDER BY (d.status = 'pending' AND d.submitted_at IS NOT NULL) DESC, (d.status = 'pending') DESC, d.created_at DESC
         LIMIT 200",
    )
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(rows))
}

#[derive(Deserialize)]
pub struct UpdateDriverReq {
    status: String,
    /// Shown to the driver when an application is sent back or an account is suspended.
    #[serde(default)]
    note: Option<String>,
}

#[derive(Serialize, FromRow)]
pub struct AdminDriverProfile {
    user_id: Uuid,
    first_name: String,
    last_name: String,
    phone_e164: Option<String>,
    email: Option<String>,
    status: String,
    licence_number: String,
    licence_expiry: Option<chrono::NaiveDate>,
    submitted_at: Option<DateTime<Utc>>,
    approved_at: Option<DateTime<Utc>>,
    review_note: Option<String>,
    created_at: DateTime<Utc>,
}

#[derive(Serialize, FromRow)]
pub struct AdminVehicle {
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
pub struct AdminPayout {
    account_name: String,
    account_number: String,
    tax_number: String,
    gst_registered: bool,
    payout_schedule: String,
}

#[derive(FromRow)]
struct AdminDocRow {
    category: String,
    name: String,
    description: String,
    required: bool,
    media_id: Option<Uuid>,
    status: Option<String>,
    rejection_reason: Option<String>,
    uploaded_at: Option<DateTime<Utc>>,
    object_key: Option<String>,
    content_type: Option<String>,
}

#[derive(Serialize)]
pub struct AdminDriverDoc {
    category: String,
    name: String,
    description: String,
    required: bool,
    media_id: Option<Uuid>,
    status: Option<String>,
    rejection_reason: Option<String>,
    uploaded_at: Option<DateTime<Utc>>,
    content_type: Option<String>,
    /// Signed S3 link, valid 15 minutes.
    view_url: Option<String>,
}

#[derive(Serialize)]
pub struct AdminDriverDetail {
    driver: AdminDriverProfile,
    vehicle: Option<AdminVehicle>,
    products: Vec<String>,
    payout: Option<AdminPayout>,
    documents: Vec<AdminDriverDoc>,
    completed_trips: i64,
    /// True when every required document is uploaded or verified (approve is allowed).
    ready_for_approval: bool,
}

/// Everything needed to review one application: who, which car, payout, and every document.
async fn driver_detail(State(st): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>) -> Result<Json<AdminDriverDetail>, ApiError> {
    require_admin(&st, &headers).await?;
    let driver = sqlx::query_as::<_, AdminDriverProfile>(
        "SELECT d.user_id, u.first_name, u.last_name, u.phone_e164, u.email, d.status, d.licence_number,
                d.licence_expiry, d.submitted_at, d.approved_at, d.review_note, d.created_at
         FROM drivers d JOIN users u ON u.id = d.user_id WHERE d.user_id = $1",
    )
    .bind(id)
    .fetch_optional(&st.pool)
    .await?
    .ok_or(ApiError::NotFound("driver_not_found"))?;
    let vehicle = sqlx::query_as::<_, AdminVehicle>(
        "SELECT make, model, color, plate, year, seats, is_electric, wheelchair_accessible FROM vehicles WHERE driver_id = $1 AND active",
    )
    .bind(id)
    .fetch_optional(&st.pool)
    .await?;
    let products: Vec<String> = sqlx::query_scalar(
        "SELECT p.name FROM driver_products dp JOIN ride_products p ON p.id = dp.product_id
         WHERE dp.driver_id = $1 AND dp.enabled ORDER BY p.sort_order",
    )
    .bind(id)
    .fetch_all(&st.pool)
    .await?;
    let payout = sqlx::query_as::<_, AdminPayout>(
        "SELECT account_name, account_number, tax_number, gst_registered, payout_schedule FROM driver_payout_accounts WHERE user_id = $1",
    )
    .bind(id)
    .fetch_optional(&st.pool)
    .await?;
    let rows = sqlx::query_as::<_, AdminDocRow>(
        "SELECT t.category, t.name, t.description, t.required,
                m.id AS media_id, m.status, m.rejection_reason, m.created_at AS uploaded_at, m.object_key, m.content_type
         FROM driver_document_types t
         LEFT JOIN LATERAL (
            SELECT id, status, rejection_reason, created_at, object_key, content_type FROM media_objects
            WHERE owner_user_id = $1 AND category = t.category AND status <> 'pending'
            ORDER BY created_at DESC LIMIT 1
         ) m ON true
         ORDER BY t.sort_order",
    )
    .bind(id)
    .fetch_all(&st.pool)
    .await?;
    let ready_for_approval = vehicle.is_some()
        && rows.iter().all(|d| !d.required || matches!(d.status.as_deref(), Some("uploaded" | "verified")));
    let documents = rows
        .into_iter()
        .map(|d| AdminDriverDoc {
            view_url: d.object_key.as_deref().and_then(|k| st.cfg.s3.as_ref().map(|s3| s3.presign_get(k, 900))),
            category: d.category,
            name: d.name,
            description: d.description,
            required: d.required,
            media_id: d.media_id,
            status: d.status,
            rejection_reason: d.rejection_reason,
            uploaded_at: d.uploaded_at,
            content_type: d.content_type,
        })
        .collect();
    let completed_trips: i64 = sqlx::query_scalar("SELECT count(*) FROM rides WHERE driver_id = $1 AND status = 'completed'")
        .bind(id)
        .fetch_one(&st.pool)
        .await?;
    Ok(Json(AdminDriverDetail { driver, vehicle, products, payout, documents, completed_trips, ready_for_approval }))
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
    let note = req.note.as_deref().map(str::trim).filter(|n| !n.is_empty()).map(|n| n.chars().take(500).collect::<String>());
    if req.status == "suspended" {
        required_reason(note.as_deref())?;
    }
    if req.status == "approved" {
        // Approval means the documents were checked: all of them must be there.
        let missing: bool = sqlx::query_scalar(
            "SELECT EXISTS (
                SELECT 1 FROM driver_document_types t
                WHERE t.required AND NOT EXISTS (
                    SELECT 1 FROM media_objects m WHERE m.owner_user_id = $1 AND m.category = t.category
                      AND m.status IN ('uploaded', 'verified')))
             OR NOT EXISTS (SELECT 1 FROM vehicles WHERE driver_id = $1 AND active)",
        )
        .bind(id)
        .fetch_one(&st.pool)
        .await?;
        if missing {
            return Err(ApiError::Conflict("documents_missing"));
        }
    }
    let done = sqlx::query(
        "UPDATE drivers SET status = $1, updated_at = now(), review_note = $3,
                approved_at = CASE WHEN $1 = 'approved' THEN COALESCE(approved_at, now()) ELSE approved_at END
         WHERE user_id = $2",
    )
    .bind(&req.status)
    .bind(id)
    .bind(&note)
    .execute(&st.pool)
    .await?;
    if done.rows_affected() == 0 {
        return Err(ApiError::NotFound("driver_not_found"));
    }
    if req.status == "approved" {
        sqlx::query(
            "UPDATE media_objects SET status = 'verified', reviewed_by = $2, reviewed_at = now(), updated_at = now()
             WHERE owner_user_id = $1 AND status = 'uploaded' AND category IN (SELECT category FROM driver_document_types)",
        )
        .bind(id)
        .bind(admin.id)
        .execute(&st.pool)
        .await?;
    }
    let (title, body) = match req.status.as_str() {
        "approved" => ("You're approved to drive".to_string(), "Welcome to Nova. Go online whenever you're ready.".to_string()),
        "suspended" => ("Your driver account is paused".to_string(), note.clone().unwrap_or_else(|| "Contact driver support for details.".into())),
        _ => ("Your application needs changes".to_string(), note.clone().unwrap_or_else(|| "Check your documents in the Nova Driver app.".into())),
    };
    crate::account::notify(&st, id, "account", &format!("driver_{}", req.status), &title, &body, None).await;
    st.hub.send(id, serde_json::json!({ "type": "driver_status", "status": req.status }));
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
    let name = display_name(&st, id).await;
    let summary = match req.status.as_str() {
        "approved" => format!("Approved {name} to drive"),
        "suspended" => format!("Suspended {name} from driving"),
        _ => format!("Sent {name}'s application back for changes"),
    };
    audit(&st, admin.id, &format!("driver.{}", req.status), "driver", id, &summary, note.as_deref()).await;
    Ok(axum::http::StatusCode::NO_CONTENT)
}

// ---------- rides ----------

#[derive(Serialize, FromRow)]
pub struct AdminRide {
    id: Uuid,
    rider_id: Uuid,
    driver_id: Option<Uuid>,
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
        "SELECT r.id, r.rider_id, r.driver_id, r.status, r.product_id, sa.name AS service_area_name,
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

#[derive(Serialize, FromRow)]
pub struct AdminRideDetail {
    id: Uuid,
    status: String,
    product_id: String,
    service_area_name: String,
    rider_id: Uuid,
    rider_name: String,
    rider_phone: Option<String>,
    passenger_name: Option<String>,
    driver_id: Option<Uuid>,
    driver_name: Option<String>,
    driver_phone: Option<String>,
    vehicle: Option<String>,
    pickup_name: String,
    pickup_address: String,
    dropoff_name: String,
    dropoff_address: String,
    currency: String,
    fare_cents: i64,
    surge_multiplier: f64,
    distance_meters: i64,
    duration_seconds: i64,
    payment_method: String,
    cancel_reason: Option<String>,
    scheduled_at: Option<DateTime<Utc>>,
    created_at: DateTime<Utc>,
    accepted_at: Option<DateTime<Utc>>,
    arrived_at: Option<DateTime<Utc>>,
    started_at: Option<DateTime<Utc>>,
    completed_at: Option<DateTime<Utc>>,
    ended_at: Option<DateTime<Utc>>,
    offers_sent: i64,
    offers_declined: i64,
}

/// One trip end to end: who, which car, money, and the dispatch timeline.
async fn ride_detail(State(st): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>) -> Result<Json<AdminRideDetail>, ApiError> {
    require_admin(&st, &headers).await?;
    let row = sqlx::query_as::<_, AdminRideDetail>(
        "SELECT r.id, r.status, r.product_id, sa.name AS service_area_name,
                r.rider_id, trim(concat(ru.first_name, ' ', ru.last_name)) AS rider_name, ru.phone_e164 AS rider_phone,
                r.passenger_name, r.driver_id,
                CASE WHEN du.id IS NOT NULL THEN trim(concat(du.first_name, ' ', du.last_name)) END AS driver_name,
                du.phone_e164 AS driver_phone,
                CASE WHEN v.id IS NOT NULL THEN concat_ws(' ', v.color, v.make, v.model) || ' · ' || v.plate END AS vehicle,
                r.pickup_name, r.pickup_address, r.dropoff_name, r.dropoff_address, r.currency, r.fare_cents,
                r.surge_multiplier, r.distance_meters, r.duration_seconds, r.payment_method, r.cancel_reason,
                r.scheduled_at, r.created_at, r.accepted_at, r.arrived_at, r.started_at, r.completed_at, r.ended_at,
                (SELECT count(*) FROM ride_offers o WHERE o.ride_id = r.id) AS offers_sent,
                (SELECT count(*) FROM ride_offers o WHERE o.ride_id = r.id AND o.response IN ('declined', 'expired')) AS offers_declined
         FROM rides r
         JOIN users ru ON ru.id = r.rider_id
         JOIN service_areas sa ON sa.id = r.service_area_id
         LEFT JOIN users du ON du.id = r.driver_id
         LEFT JOIN vehicles v ON v.id = r.vehicle_id
         WHERE r.id = $1",
    )
    .bind(id)
    .fetch_optional(&st.pool)
    .await?
    .ok_or(ApiError::NotFound("ride_not_found"))?;
    Ok(Json(row))
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
    let admin = require_admin(&st, &headers).await?;
    let done = sqlx::query("UPDATE service_areas SET active = $1, updated_at = now() WHERE id = $2")
        .bind(req.active)
        .bind(id)
        .execute(&st.pool)
        .await?;
    if done.rows_affected() == 0 {
        return Err(ApiError::NotFound("service_area_not_found"));
    }
    let name: String = sqlx::query_scalar("SELECT name FROM service_areas WHERE id = $1").bind(id).fetch_one(&st.pool).await?;
    let summary = if req.active { format!("Opened {name} for bookings") } else { format!("Paused bookings in {name}") };
    audit(&st, admin.id, if req.active { "service_area.opened" } else { "service_area.paused" }, "service_area", id, &summary, None).await;
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
    let reason = if req.status == "rejected" { Some(required_reason(req.rejection_reason.as_deref())?) } else { None };
    let done = sqlx::query(
        "UPDATE media_objects
         SET status = $1, reviewed_by = $2, reviewed_at = now(), rejection_reason = $3, updated_at = now()
         WHERE id = $4",
    )
    .bind(&req.status)
    .bind(admin.id)
    .bind(reason.as_deref())
    .bind(id)
    .execute(&st.pool)
    .await?;
    if done.rows_affected() == 0 {
        return Err(ApiError::NotFound("media_not_found"));
    }
    let (owner, label): (Uuid, String) = sqlx::query_as(
        "SELECT m.owner_user_id, COALESCE(t.name, replace(m.category, '_', ' ')) FROM media_objects m
         LEFT JOIN driver_document_types t ON t.category = m.category WHERE m.id = $1",
    )
    .bind(id)
    .fetch_one(&st.pool)
    .await?;
    let owner_name = display_name(&st, owner).await;
    let summary = if req.status == "verified" { format!("Verified {owner_name}'s {label}") } else { format!("Sent back {owner_name}'s {label}") };
    audit(&st, admin.id, &format!("media.{}", req.status), "media", id, &summary, reason.as_deref()).await;
    if req.status == "rejected" {
        // A driver document sent back: tell the driver what to re-upload.
        let doc: Option<(Uuid, String)> = sqlx::query_as(
            "SELECT m.owner_user_id, t.name FROM media_objects m JOIN driver_document_types t ON t.category = m.category WHERE m.id = $1",
        )
        .bind(id)
        .fetch_optional(&st.pool)
        .await?;
        if let Some((owner, name)) = doc {
            let reason = reason.clone().unwrap_or_else(|| "Please upload a clearer photo.".into());
            crate::account::notify(&st, owner, "account", "document_rejected", &format!("{name} needs a new photo"), &reason, None).await;
            st.hub.send(owner, serde_json::json!({ "type": "driver_status", "status": "document_rejected" }));
        }
    }
    Ok(axum::http::StatusCode::NO_CONTENT)
}

async fn delete_media(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
) -> Result<axum::http::StatusCode, ApiError> {
    let admin = require_admin(&st, &headers).await?;
    // Driver documents and avatars are records of a person: they are sent back, never deleted.
    let category: Option<String> = sqlx::query_scalar("SELECT category FROM media_objects WHERE id = $1").bind(id).fetch_optional(&st.pool).await?;
    match category.as_deref() {
        None => return Err(ApiError::NotFound("media_not_found")),
        Some("admin_asset" | "support_attachment") => {}
        Some(_) => return Err(ApiError::Conflict("record_kept")),
    }
    let file_name: String = sqlx::query_scalar("SELECT file_name FROM media_objects WHERE id = $1").bind(id).fetch_one(&st.pool).await?;
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
    audit(&st, admin.id, "media.deleted", "media", id, &format!("Deleted file {file_name}"), None).await;
    Ok(axum::http::StatusCode::NO_CONTENT)
}
