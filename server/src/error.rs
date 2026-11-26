use axum::{
    http::{header, StatusCode},
    response::{IntoResponse, Response},
    Json,
};
use serde_json::json;

/// Every API error is `{ "error": "<stable_code>" }` so the apps can map codes to messages.
#[derive(Debug)]
pub enum ApiError {
    BadRequest(&'static str),
    Unauthorized,
    /// Signed in, but this account may not do this (e.g. a rider account on the driver API).
    Forbidden(&'static str),
    NotFound(&'static str),
    Conflict(&'static str),
    /// Seconds until the client may retry.
    TooManyRequests(i64),
    /// A downstream provider (Twilio, Resend) is missing or failing.
    Unavailable(&'static str),
    Internal,
}

impl ApiError {
    /// The stable code the apps see, for channels other than HTTP (the live socket).
    pub fn code(&self) -> &'static str {
        match self {
            ApiError::BadRequest(c) | ApiError::Forbidden(c) | ApiError::NotFound(c) | ApiError::Conflict(c) | ApiError::Unavailable(c) => c,
            ApiError::Unauthorized => "unauthorized",
            ApiError::TooManyRequests(_) => "too_many_requests",
            ApiError::Internal => "internal_error",
        }
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let (status, code) = match self {
            ApiError::BadRequest(c) => (StatusCode::BAD_REQUEST, c),
            ApiError::Unauthorized => (StatusCode::UNAUTHORIZED, "unauthorized"),
            ApiError::Forbidden(c) => (StatusCode::FORBIDDEN, c),
            ApiError::NotFound(c) => (StatusCode::NOT_FOUND, c),
            ApiError::Conflict(c) => (StatusCode::CONFLICT, c),
            ApiError::TooManyRequests(secs) => {
                return (
                    StatusCode::TOO_MANY_REQUESTS,
                    [(header::RETRY_AFTER, secs.to_string())],
                    Json(json!({ "error": "too_many_requests", "retry_after": secs })),
                )
                    .into_response();
            }
            ApiError::Unavailable(c) => (StatusCode::SERVICE_UNAVAILABLE, c),
            ApiError::Internal => (StatusCode::INTERNAL_SERVER_ERROR, "internal_error"),
        };
        (status, Json(json!({ "error": code }))).into_response()
    }
}

impl From<redis::RedisError> for ApiError {
    fn from(err: redis::RedisError) -> Self {
        tracing::error!(error = ?err, "redis error");
        ApiError::Internal
    }
}

impl From<sqlx::Error> for ApiError {
    fn from(err: sqlx::Error) -> Self {
        tracing::error!(error = ?err, "database error");
        ApiError::Internal
    }
}

/// True when a Postgres unique constraint was violated.
pub fn is_unique_violation(err: &sqlx::Error) -> bool {
    matches!(err, sqlx::Error::Database(db) if db.code().as_deref() == Some("23505"))
}
