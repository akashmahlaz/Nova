//! Signed S3 media upload, confirmation, and view URLs for Nova Rider, Driver, and Admin.

use axum::{
    extract::{Path, State},
    http::HeaderMap,
    routing::{get, post},
    Json, Router,
};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

use crate::{
    auth::{load_user, require_user_id},
    config::S3Config,
    error::ApiError,
    AppState,
};

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/media/presign-upload", post(presign_upload))
        .route("/v1/media/{id}/confirm", post(confirm_upload))
        .route("/v1/media/{id}/url", get(get_signed_url))
        .route("/v1/media/mine", get(list_mine))
}

fn require_s3(st: &AppState) -> Result<&S3Config, ApiError> {
    st.cfg.s3.as_ref().ok_or(ApiError::Unavailable("s3_unavailable"))
}

fn extension_for(content_type: &str) -> Option<&'static str> {
    match content_type {
        "image/jpeg" | "image/jpg" => Some("jpg"),
        "image/png" => Some("png"),
        "image/webp" => Some("webp"),
        "application/pdf" => Some("pdf"),
        _ => None,
    }
}

#[derive(Deserialize)]
pub struct PresignUploadReq {
    category: String,
    content_type: String,
    #[serde(default)]
    file_name: String,
}

#[derive(Serialize)]
pub struct PresignUploadRes {
    media_id: Uuid,
    bucket: String,
    object_key: String,
    upload_url: String,
    expires_in: u64,
}

async fn presign_upload(
    State(st): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<PresignUploadReq>,
) -> Result<Json<PresignUploadRes>, ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    let s3 = require_s3(&st)?;

    if !matches!(
        req.category.as_str(),
        "avatar" | "driver_licence" | "driver_insurance" | "vehicle_reg" | "support_attachment" | "admin_asset"
    ) {
        return Err(ApiError::BadRequest("invalid_category"));
    }
    if req.category == "admin_asset" {
        let user = load_user(&st, user_id).await?;
        if user.role != "admin" {
            return Err(ApiError::Unauthorized);
        }
    }
    let ext = extension_for(req.content_type.trim()).ok_or(ApiError::BadRequest("invalid_content_type"))?;

    let media_id = Uuid::now_v7();
    let object_key = format!("{}/{}/{}.{}", req.category, user_id, media_id, ext);
    let safe_name: String = req
        .file_name
        .trim()
        .chars()
        .filter(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_' | ' '))
        .take(120)
        .collect();

    sqlx::query(
        "INSERT INTO media_objects (id, owner_user_id, bucket, object_key, category, file_name, content_type, status)
         VALUES ($1, $2, $3, $4, $5, $6, $7, 'pending')",
    )
    .bind(media_id)
    .bind(user_id)
    .bind(&s3.bucket)
    .bind(&object_key)
    .bind(&req.category)
    .bind(&safe_name)
    .bind(req.content_type.trim())
    .execute(&st.pool)
    .await?;

    let expires_in = 300;
    let upload_url = s3.presign_put(&object_key, expires_in);

    Ok(Json(PresignUploadRes {
        media_id,
        bucket: s3.bucket.clone(),
        object_key,
        upload_url,
        expires_in,
    }))
}

#[derive(FromRow)]
struct MediaRow {
    id: Uuid,
    owner_user_id: Uuid,
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
pub struct MediaItem {
    id: Uuid,
    object_key: String,
    category: String,
    file_name: String,
    content_type: String,
    size_bytes: i64,
    status: String,
    rejection_reason: Option<String>,
    view_url: String,
    created_at: DateTime<Utc>,
}

async fn confirm_upload(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
) -> Result<Json<MediaItem>, ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    let s3 = require_s3(&st)?;

    let row = sqlx::query_as::<_, MediaRow>(
        "SELECT id, owner_user_id, object_key, category, file_name, content_type, size_bytes, status, rejection_reason, created_at
         FROM media_objects WHERE id = $1",
    )
    .bind(id)
    .fetch_optional(&st.pool)
    .await?
    .ok_or(ApiError::NotFound("media_not_found"))?;

    if row.owner_user_id != user_id {
        let user = load_user(&st, user_id).await?;
        if user.role != "admin" {
            return Err(ApiError::Unauthorized);
        }
    }

    let size = match s3.head_object(&st.http, &row.object_key).await {
        Ok(Some(len)) => len as i64,
        Ok(None) => return Err(ApiError::BadRequest("object_not_uploaded")),
        Err(err) => {
            tracing::error!(error = %err, key = %row.object_key, "s3 head_object error");
            return Err(ApiError::Unavailable("s3_unavailable"));
        }
    };

    let next_status = if row.category == "admin_asset" { "verified" } else { "uploaded" };
    sqlx::query(
        "UPDATE media_objects SET status = $1, size_bytes = $2, updated_at = now() WHERE id = $3",
    )
    .bind(next_status)
    .bind(size)
    .bind(id)
    .execute(&st.pool)
    .await?;

    Ok(Json(MediaItem {
        id: row.id,
        object_key: row.object_key.clone(),
        category: row.category,
        file_name: row.file_name,
        content_type: row.content_type,
        size_bytes: size,
        status: next_status.to_string(),
        rejection_reason: row.rejection_reason,
        view_url: s3.presign_get(&row.object_key, 900),
        created_at: row.created_at,
    }))
}

#[derive(Serialize)]
pub struct SignedUrlRes {
    media_id: Uuid,
    view_url: String,
    expires_in: u64,
}

async fn get_signed_url(
    State(st): State<AppState>,
    headers: HeaderMap,
    Path(id): Path<Uuid>,
) -> Result<Json<SignedUrlRes>, ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    let s3 = require_s3(&st)?;

    let row = sqlx::query_as::<_, MediaRow>(
        "SELECT id, owner_user_id, object_key, category, file_name, content_type, size_bytes, status, rejection_reason, created_at
         FROM media_objects WHERE id = $1",
    )
    .bind(id)
    .fetch_optional(&st.pool)
    .await?
    .ok_or(ApiError::NotFound("media_not_found"))?;

    if row.owner_user_id != user_id {
        let user = load_user(&st, user_id).await?;
        if user.role != "admin" {
            return Err(ApiError::Unauthorized);
        }
    }

    let expires_in = 900;
    Ok(Json(SignedUrlRes {
        media_id: row.id,
        view_url: s3.presign_get(&row.object_key, expires_in),
        expires_in,
    }))
}

async fn list_mine(
    State(st): State<AppState>,
    headers: HeaderMap,
) -> Result<Json<Vec<MediaItem>>, ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    let s3 = require_s3(&st)?;

    let rows = sqlx::query_as::<_, MediaRow>(
        "SELECT id, owner_user_id, object_key, category, file_name, content_type, size_bytes, status, rejection_reason, created_at
         FROM media_objects
         WHERE owner_user_id = $1 AND status <> 'pending'
         ORDER BY created_at DESC
         LIMIT 50",
    )
    .bind(user_id)
    .fetch_all(&st.pool)
    .await?;

    let items = rows
        .into_iter()
        .map(|r| {
            let view_url = s3.presign_get(&r.object_key, 900);
            MediaItem {
                id: r.id,
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
