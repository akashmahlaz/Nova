//! Rider account endpoints: preferences, the in-app notification inbox, data export and
//! account deletion.
//!
//! Deletion follows the Google Play account-deletion policy and the NZ Privacy Act 2020: the rider
//! can delete in the app; we revoke every session immediately, cancel scheduled rides, and remove
//! the account after a 30-day grace period (signing in again cancels it, see `auth::issue_session`).
//! [spawn_deletion_purge] runs the removal hourly; ON DELETE CASCADE removes the rider's rows.

use std::time::Duration as StdDuration;

use axum::{
    extract::{Query, State},
    http::{HeaderMap, StatusCode},
    routing::{get, post},
    Json, Router,
};
use chrono::{DateTime, Duration, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

use crate::{auth::require_user_id, error::ApiError, geo::rate_limit, AppState};

const DELETION_GRACE_DAYS: i64 = 30;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/me/settings", get(get_settings).put(put_settings))
        .route("/v1/me/export", get(export))
        .route("/v1/me/delete", post(request_deletion))
        .route("/v1/notifications", get(list_notifications))
        .route("/v1/notifications/read-all", post(read_all))
}

// ---------- settings ----------

#[derive(Debug, Serialize, Deserialize, FromRow)]
pub struct RiderSettings {
    wheelchair_access: bool,
    quiet_ride: bool,
    trip_location_only: bool,
}

impl Default for RiderSettings {
    fn default() -> Self {
        // Must match the column defaults in migration 0010.
        RiderSettings { wheelchair_access: false, quiet_ride: false, trip_location_only: true }
    }
}

async fn get_settings(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<RiderSettings>, ApiError> {
    let user = require_user_id(&st, &headers)?;
    let row = sqlx::query_as::<_, RiderSettings>(
        "SELECT wheelchair_access, quiet_ride, trip_location_only FROM rider_settings WHERE user_id = $1",
    )
    .bind(user)
    .fetch_optional(&st.pool)
    .await?;
    Ok(Json(row.unwrap_or_default()))
}

/// Saves the whole settings object (the app always sends every field), creating the row on first save.
async fn put_settings(
    State(st): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<RiderSettings>,
) -> Result<Json<RiderSettings>, ApiError> {
    let user = require_user_id(&st, &headers)?;
    rate_limit(&st, "settings", user, 60, 60).await?;
    let row = sqlx::query_as::<_, RiderSettings>(
        "INSERT INTO rider_settings (user_id, wheelchair_access, quiet_ride, trip_location_only)
         VALUES ($1, $2, $3, $4)
         ON CONFLICT (user_id) DO UPDATE SET
            wheelchair_access = EXCLUDED.wheelchair_access,
            quiet_ride = EXCLUDED.quiet_ride,
            trip_location_only = EXCLUDED.trip_location_only,
            updated_at = now()
         RETURNING wheelchair_access, quiet_ride, trip_location_only",
    )
    .bind(user)
    .bind(req.wheelchair_access)
    .bind(req.quiet_ride)
    .bind(req.trip_location_only)
    .fetch_one(&st.pool)
    .await?;
    Ok(Json(row))
}

// ---------- notifications ----------

#[derive(Serialize, FromRow)]
pub struct Notification {
    id: Uuid,
    kind: String,
    event: String,
    title: String,
    body: String,
    ride_id: Option<Uuid>,
    /// For a ride booked for this rider by someone else: its tracking token (opens TrackRide).
    share_token: Option<String>,
    read_at: Option<DateTime<Utc>>,
    created_at: DateTime<Utc>,
}

/// Adds a notification to a rider's inbox. Failures are logged, never returned: a missing inbox
/// entry must not fail the booking or cancellation that triggered it.
pub async fn notify(st: &AppState, user: Uuid, kind: &str, event: &str, title: &str, body: &str, ride_id: Option<Uuid>) {
    let res = sqlx::query(
        "INSERT INTO notifications (id, user_id, kind, event, title, body, ride_id) VALUES ($1, $2, $3, $4, $5, $6, $7)",
    )
    .bind(Uuid::now_v7())
    .bind(user)
    .bind(kind)
    .bind(event)
    .bind(title)
    .bind(body)
    .bind(ride_id)
    .execute(&st.pool)
    .await;
    if let Err(err) = res {
        tracing::warn!(error = ?err, %user, event, "could not write notification");
    }
}

#[derive(Deserialize)]
pub struct ListQuery {
    limit: Option<i64>,
    before: Option<DateTime<Utc>>,
}

#[derive(Serialize)]
pub struct ListRes {
    notifications: Vec<Notification>,
    unread: i64,
}

async fn list_notifications(
    State(st): State<AppState>,
    headers: HeaderMap,
    Query(q): Query<ListQuery>,
) -> Result<Json<ListRes>, ApiError> {
    let user = require_user_id(&st, &headers)?;
    let limit = q.limit.unwrap_or(30).clamp(1, 50);
    let notifications = sqlx::query_as::<_, Notification>(
        "SELECT n.id, n.kind, n.event, n.title, n.body, n.ride_id, r.share_token, n.read_at, n.created_at
         FROM notifications n
         LEFT JOIN rides r ON r.id = n.ride_id AND r.passenger_user_id = n.user_id
         WHERE n.user_id = $1 AND ($2::timestamptz IS NULL OR n.created_at < $2)
         ORDER BY n.created_at DESC LIMIT $3",
    )
    .bind(user)
    .bind(q.before)
    .bind(limit)
    .fetch_all(&st.pool)
    .await?;
    let unread: i64 = sqlx::query_scalar("SELECT count(*) FROM notifications WHERE user_id = $1 AND read_at IS NULL")
        .bind(user)
        .fetch_one(&st.pool)
        .await?;
    Ok(Json(ListRes { notifications, unread }))
}

async fn read_all(State(st): State<AppState>, headers: HeaderMap) -> Result<StatusCode, ApiError> {
    let user = require_user_id(&st, &headers)?;
    sqlx::query("UPDATE notifications SET read_at = now() WHERE user_id = $1 AND read_at IS NULL")
        .bind(user)
        .execute(&st.pool)
        .await?;
    Ok(StatusCode::NO_CONTENT)
}

// ---------- data export ----------

/// Everything we hold about the rider, as one JSON document (NZ Privacy Act 2020, principle 6:
/// the right to access personal information). Built in SQL so it is one round trip.
async fn export(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<serde_json::Value>, ApiError> {
    let user = require_user_id(&st, &headers)?;
    rate_limit(&st, "export", user, 5, 3600).await?;
    let doc: serde_json::Value = sqlx::query_scalar(
        "SELECT json_build_object(
            'exported_at', now(),
            'account', (SELECT row_to_json(u) FROM (
                SELECT id, first_name, last_name, phone_e164, email, phone_verified_at, email_verified_at, created_at
                FROM users WHERE id = $1) u),
            'settings', (SELECT row_to_json(s) FROM (
                SELECT wheelchair_access, quiet_ride, trip_location_only, updated_at
                FROM rider_settings WHERE user_id = $1) s),
            'saved_places', COALESCE((SELECT json_agg(p ORDER BY p.created_at) FROM (
                SELECT kind, label, address, lat, lng, created_at FROM saved_places WHERE user_id = $1) p), '[]'),
            'emergency_contacts', COALESCE((SELECT json_agg(c ORDER BY c.created_at) FROM (
                SELECT name, phone_e164, relationship, created_at FROM emergency_contacts WHERE user_id = $1) c), '[]'),
            'rides', COALESCE((SELECT json_agg(r ORDER BY r.created_at) FROM (
                SELECT r.id, r.status, r.product_id, r.pickup_name, r.pickup_address, r.pickup_lat, r.pickup_lng,
                       r.dropoff_name, r.dropoff_address, r.dropoff_lat, r.dropoff_lng,
                       COALESCE((SELECT json_agg(rs ORDER BY rs.stop_order) FROM (
                           SELECT stop_order, name, lat, lng FROM ride_stops WHERE ride_id = r.id
                       ) rs), '[]') AS stops,
                       r.currency, r.fare_cents,
                       r.distance_meters, r.duration_seconds, r.payment_method, r.passenger_name, r.passenger_phone,
                       r.scheduled_at, r.requested_at, r.ended_at, r.cancel_reason, r.created_at
                FROM rides r WHERE r.rider_id = $1) r), '[]'),
            'notifications', COALESCE((SELECT json_agg(n ORDER BY n.created_at) FROM (
                SELECT kind, event, title, body, read_at, created_at FROM notifications WHERE user_id = $1) n), '[]')
        )",
    )
    .bind(user)
    .fetch_one(&st.pool)
    .await?;
    Ok(Json(doc))
}

// ---------- deletion ----------

#[derive(Serialize)]
pub struct DeletionRes {
    /// When the account and its data will be removed unless the rider signs in again.
    delete_after: DateTime<Utc>,
}

async fn request_deletion(State(st): State<AppState>, headers: HeaderMap) -> Result<Json<DeletionRes>, ApiError> {
    let user = require_user_id(&st, &headers)?;
    let mut tx = st.pool.begin().await?;

    // A ride with a driver on the way can't be abandoned by deleting the account.
    let active: bool = sqlx::query_scalar(
        "SELECT EXISTS (SELECT 1 FROM rides WHERE rider_id = $1 AND status IN ('accepted', 'arriving', 'in_progress'))",
    )
    .bind(user)
    .fetch_one(&mut *tx)
    .await?;
    if active {
        return Err(ApiError::Conflict("ride_in_progress"));
    }

    let requested_at: DateTime<Utc> = sqlx::query_scalar(
        "UPDATE users SET deletion_requested_at = COALESCE(deletion_requested_at, now()), updated_at = now()
         WHERE id = $1 RETURNING deletion_requested_at",
    )
    .bind(user)
    .fetch_one(&mut *tx)
    .await?;
    sqlx::query(
        "UPDATE rides SET status = 'cancelled', cancel_reason = 'account_deleted', ended_at = now(), updated_at = now()
         WHERE rider_id = $1 AND status IN ('scheduled', 'requested')",
    )
    .bind(user)
    .execute(&mut *tx)
    .await?;
    // Sign out everywhere. Access tokens are short-lived and expire on their own.
    sqlx::query("UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL")
        .bind(user)
        .execute(&mut *tx)
        .await?;
    tx.commit().await?;

    tracing::info!(%user, "account deletion requested");
    Ok(Json(DeletionRes { delete_after: requested_at + Duration::days(DELETION_GRACE_DAYS) }))
}

/// Hourly: permanently delete accounts whose grace period is over.
pub fn spawn_deletion_purge(pool: sqlx::PgPool) {
    tokio::spawn(async move {
        let mut tick = tokio::time::interval(StdDuration::from_secs(3600));
        loop {
            tick.tick().await;
            let res = sqlx::query(
                "DELETE FROM users WHERE deletion_requested_at < now() - make_interval(days => $1)",
            )
            .bind(DELETION_GRACE_DAYS as i32)
            .execute(&pool)
            .await;
            match res {
                Ok(done) if done.rows_affected() > 0 => {
                    tracing::info!(count = done.rows_affected(), "deleted accounts past their grace period")
                }
                Ok(_) => {}
                Err(err) => tracing::error!(error = ?err, "account deletion purge failed"),
            }
        }
    });
}
