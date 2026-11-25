//! "Who's riding?" contact matching: which of the rider's phone contacts have a Nova account.
//!
//! Privacy model (the contact-discovery pattern messaging apps use, kept deliberately minimal):
//!   * The app normalises each contact number to E.164 and sends only its SHA-256, never the
//!     number or the contact's name.
//!   * We answer with the subset of hashes that belong to verified Nova accounts and store
//!     nothing: no table, no log of the request body.
//!   * Phone-number hashes can be brute-forced offline (the number space is small), so the real
//!     protections are the per-call cap and the per-rider rate limit, which stop this endpoint
//!     from being used to check the whole number space against Nova.
//!   * The app asks for consent (prominent disclosure, Google Play User Data policy) before it
//!     reads contacts.

use axum::{extract::State, http::HeaderMap, routing::post, Json, Router};
use serde::{Deserialize, Serialize};

use crate::{auth::require_user_id, error::ApiError, geo::rate_limit, AppState};

/// Hashes per request. A phone book is usually a few hundred numbers; the app sends batches.
const MAX_HASHES: usize = 1000;
/// Requests per rider per hour (a full sync of a large phone book is a handful of batches).
const MATCHES_PER_HOUR: i64 = 20;

pub fn router() -> Router<AppState> {
    Router::new().route("/v1/contacts/match", post(match_contacts))
}

#[derive(Deserialize)]
pub struct MatchReq {
    /// Lowercase hex SHA-256 of E.164 numbers, e.g. sha256("+64211234567").
    hashes: Vec<String>,
}

#[derive(Serialize)]
pub struct MatchRes {
    /// The hashes from the request that belong to Nova riders (never the caller's own).
    on_nova: Vec<String>,
}

async fn match_contacts(
    State(st): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<MatchReq>,
) -> Result<Json<MatchRes>, ApiError> {
    let user = require_user_id(&st, &headers)?;
    if req.hashes.len() > MAX_HASHES {
        return Err(ApiError::BadRequest("too_many_contacts"));
    }
    let valid = |h: &String| h.len() == 64 && h.bytes().all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b));
    if !req.hashes.iter().all(valid) {
        return Err(ApiError::BadRequest("invalid_contact_hash"));
    }
    if req.hashes.is_empty() {
        return Ok(Json(MatchRes { on_nova: vec![] }));
    }
    rate_limit(&st, "contacts_match", user, MATCHES_PER_HOUR, 3600).await?;
    let on_nova: Vec<String> = sqlx::query_scalar(
        "SELECT phone_sha256 FROM users
         WHERE phone_sha256 = ANY($1) AND phone_verified_at IS NOT NULL
           AND deletion_requested_at IS NULL AND id <> $2",
    )
    .bind(&req.hashes)
    .bind(user)
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(MatchRes { on_nova }))
}
