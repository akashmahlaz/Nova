//! Admin-only API endpoints gated by `users.role = 'admin'`.

use axum::{
    extract::{Path, State},
    http::HeaderMap,
    routing::{get, patch},
    Json, Router,
};
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
        .route("/v1/admin/users", get(list_users))
        .route("/v1/admin/users/{id}/role", patch(update_role))
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
