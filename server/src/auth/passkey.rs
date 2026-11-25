//! Passkey (WebAuthn) registration and login.
//!
//! Flows
//! - Register: `POST /v1/auth/passkey/register/options` (authenticated) returns the creation
//!   challenge; the client creates the passkey with Credential Manager and posts the result to
//!   `/register/verify`, where the public key is stored.
//! - Login: `POST /v1/auth/passkey/login/options` returns the assertion challenge (discoverable
//!   credentials, so no username is needed); the client signs in with a passkey and posts the
//!   assertion to `/login/verify`, which returns a session.
//!
//! The challenge state (`PasskeyRegistration` / `DiscoverableAuthentication`) is kept in Redis for
//! 5 minutes between the options and verify calls.

use axum::{
    extract::State,
    http::HeaderMap,
    response::IntoResponse,
    routing::post,
    Json, Router,
};
use redis::AsyncCommands;
use serde::{Deserialize, Serialize};
use serde_json::json;
use sqlx::FromRow;
use uuid::Uuid;
use webauthn_rs::prelude::*;
use webauthn_rs_proto::{AuthenticatorTransport, RegisteredExtensions, UserVerificationPolicy};

use crate::{error::ApiError, AppState};
use super::{issue_session, load_user, User};

const CHALLENGE_TTL_SECS: u64 = 300;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/auth/passkey/register/options", post(register_options))
        .route("/v1/auth/passkey/register/verify", post(register_verify))
        .route("/v1/auth/passkey/login/options", post(login_options))
        .route("/v1/auth/passkey/login/verify", post(login_verify))
}

fn android_apk_origin(hex_fp: &str) -> Option<Url> {
    let clean: String = hex_fp.chars().filter(|c| c.is_ascii_hexdigit()).collect();
    if clean.len() != 64 {
        return None;
    }
    let bytes = hex::decode(&clean).ok()?;
    let b64 = base64url_encode(&bytes);
    Url::parse(&format!("android:apk-key-hash:{b64}")).ok()
}

fn build_webauthn(st: &AppState) -> Result<Webauthn, ApiError> {
    let origin = Url::parse(&st.cfg.passkey_rp_origin).map_err(|_| ApiError::Internal)?;
    let mut builder = WebauthnBuilder::new(&st.cfg.passkey_rp_id, &origin).map_err(|_| ApiError::Internal)?;
    if let Ok(raw) = std::env::var("PASSKEY_ASSETLINKS_FINGERPRINT") {
        for fp in raw.split(',').map(str::trim).filter(|s| !s.is_empty()) {
            if let Some(apk_origin) = android_apk_origin(fp) {
                builder = builder.append_allowed_origin(&apk_origin);
            }
        }
    }
    builder.build().map_err(|_| ApiError::Internal)
}

/// Extracts the user from a `Authorization: Bearer <token>` header.
async fn user_from_headers(st: &AppState, headers: &HeaderMap) -> Result<User, ApiError> {
    let token = headers
        .get(axum::http::header::AUTHORIZATION)
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "))
        .ok_or(ApiError::Unauthorized)?;
    let claims = super::jwt::decode_typed(&st.cfg.jwt_secret, token, super::jwt::TYP_ACCESS)
        .map_err(|_| ApiError::Unauthorized)?;
    let user_id = Uuid::parse_str(&claims.sub).map_err(|_| ApiError::Unauthorized)?;
    load_user(st, user_id).await
}

// ---------- register ----------

async fn register_options(
    State(st): State<AppState>,
    headers: HeaderMap,
) -> Result<impl IntoResponse, ApiError> {
    let user = user_from_headers(&st, &headers).await?;
    let webauthn = build_webauthn(&st)?;

    let username = user
        .email
        .clone()
        .or(user.phone_e164.clone())
        .unwrap_or_else(|| user.id.to_string());

    let (ccr, reg_state) = webauthn
        .start_passkey_registration(user.id, &username, &user.first_name, None)
        .map_err(|_| ApiError::BadRequest("passkey_options_failed"))?;

    let mut redis = st.redis.clone();
    let key = format!("nova:passkey:reg:{}", user.id);
    let state_json = serde_json::to_string(&reg_state).map_err(|_| ApiError::Internal)?;
    let _: () = redis.set_ex(&key, state_json, CHALLENGE_TTL_SECS).await?;

    Ok(Json(ccr))
}

#[derive(Deserialize)]
pub struct RegisterVerifyReq {
    /// The `PublicKeyCredential` JSON produced by Credential Manager's `createCredential()`.
    credential: String,
}

#[derive(Serialize)]
pub struct RegisterVerifyRes {
    pub ok: bool,
    pub credential_id: String,
}

async fn register_verify(
    State(st): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<RegisterVerifyReq>,
) -> Result<Json<RegisterVerifyRes>, ApiError> {
    let user = user_from_headers(&st, &headers).await?;
    let webauthn = build_webauthn(&st)?;

    let mut redis = st.redis.clone();
    let key = format!("nova:passkey:reg:{}", user.id);
    let state_json: Option<String> = redis.get(&key).await?;
    let state_json = state_json.ok_or(ApiError::BadRequest("passkey_challenge_expired"))?;
    let reg_state: PasskeyRegistration =
        serde_json::from_str(&state_json).map_err(|_| ApiError::Internal)?;

    let reg_credential: RegisterPublicKeyCredential =
        serde_json::from_str(&req.credential).map_err(|_| ApiError::BadRequest("passkey_invalid"))?;

    let passkey = webauthn
        .finish_passkey_registration(&reg_credential, &reg_state)
        .map_err(|err| {
            tracing::warn!(error = ?err, "passkey registration verification failed");
            ApiError::BadRequest("passkey_verification_failed")
        })?;

    let cred_id = base64url_encode(passkey.cred_id().as_ref());

    // Reject duplicate credentials.
    let exists: Option<Uuid> = sqlx::query_scalar(
        "SELECT id FROM passkey_credentials WHERE credential_id = $1",
    )
    .bind(&cred_id)
    .fetch_optional(&st.pool)
    .await?;
    if exists.is_some() {
        return Err(ApiError::Conflict("passkey_already_registered"));
    }

    let cred: Credential = passkey.into();
    let public_key_cbor = serde_cbor_2::to_vec(&cred.cred).map_err(|_| ApiError::Internal)?;
    let policy_str = match cred.registration_policy {
        UserVerificationPolicy::Required => "required",
        UserVerificationPolicy::Preferred => "preferred",
        UserVerificationPolicy::Discouraged_DO_NOT_USE => "discouraged",
    };
    let fmt_str = format!("{:?}", cred.attestation_format);
    let transports: Option<Vec<String>> = cred.transports.as_ref().map(|ts| {
        ts.iter().map(|t| format!("{:?}", t).to_lowercase()).collect()
    });

    sqlx::query(
        "INSERT INTO passkey_credentials (
            id, user_id, credential_id, cred_id_raw, public_key_cbor,
            counter, user_verified, backup_eligible, backup_state,
            registration_policy, attestation_format, transports
        ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)",
    )
    .bind(Uuid::now_v7())
    .bind(user.id)
    .bind(&cred_id)
    .bind(cred.cred_id.as_slice())
    .bind(&public_key_cbor)
    .bind(cred.counter as i64)
    .bind(cred.user_verified)
    .bind(cred.backup_eligible)
    .bind(cred.backup_state)
    .bind(policy_str)
    .bind(&fmt_str)
    .bind(&transports)
    .execute(&st.pool)
    .await?;

    // Consume the challenge.
    let _: () = redis.del(&key).await?;

    Ok(Json(RegisterVerifyRes { ok: true, credential_id: cred_id }))
}

// ---------- login (discoverable credentials — no username needed) ----------

async fn login_options(State(st): State<AppState>) -> Result<impl IntoResponse, ApiError> {
    let webauthn = build_webauthn(&st)?;

    let (rcr, auth_state) = webauthn
        .start_discoverable_authentication()
        .map_err(|_| ApiError::BadRequest("passkey_options_failed"))?;

    let state_json = serde_json::to_string(&auth_state).map_err(|_| ApiError::Internal)?;
    let state_token = Uuid::new_v4().to_string();

    let mut redis = st.redis.clone();
    let key = format!("nova:passkey:auth:{state_token}");
    let _: () = redis.set_ex(&key, state_json, CHALLENGE_TTL_SECS).await?;

    Ok(Json(json!({
        "options": rcr,
        "state_token": state_token,
    })))
}

#[derive(Deserialize)]
pub struct LoginVerifyReq {
    /// The `PublicKeyCredential` JSON produced by Credential Manager's `getCredential()`.
    credential: String,
    /// The opaque token returned by `/login/options`.
    state_token: String,
}

async fn login_verify(
    State(st): State<AppState>,
    Json(req): Json<LoginVerifyReq>,
) -> Result<Json<super::SessionRes>, ApiError> {
    let webauthn = build_webauthn(&st)?;

    let mut redis = st.redis.clone();
    let key = format!("nova:passkey:auth:{}", req.state_token);
    let state_json: Option<String> = redis.get(&key).await?;
    let state_json = state_json.ok_or(ApiError::BadRequest("passkey_challenge_expired"))?;
    let auth_state: DiscoverableAuthentication =
        serde_json::from_str(&state_json).map_err(|_| ApiError::Internal)?;

    let auth_credential: PublicKeyCredential =
        serde_json::from_str(&req.credential).map_err(|_| ApiError::BadRequest("passkey_invalid"))?;

    // Resolve the user and credential ID from the assertion's userHandle.
    let (user_id, _cred_id_bytes) = webauthn
        .identify_discoverable_authentication(&auth_credential)
        .map_err(|_| ApiError::Unauthorized)?;

    #[derive(FromRow)]
    struct PasskeyRow {
        cred_id_raw: Vec<u8>,
        public_key_cbor: Vec<u8>,
        counter: i64,
        user_verified: bool,
        backup_eligible: bool,
        backup_state: bool,
        registration_policy: String,
        transports: Option<Vec<String>>,
    }

    // Load the stored passkey from normalized relational columns.
    let cred_id_b64 = base64url_encode(_cred_id_bytes);
    let row = sqlx::query_as::<_, PasskeyRow>(
        "SELECT cred_id_raw, public_key_cbor, counter, user_verified, backup_eligible,
                backup_state, registration_policy, transports
         FROM passkey_credentials
         WHERE credential_id = $1 AND user_id = $2",
    )
    .bind(&cred_id_b64)
    .bind(user_id)
    .fetch_optional(&st.pool)
    .await?
    .ok_or(ApiError::Unauthorized)?;

    let cose_key: COSEKey = serde_cbor_2::from_slice(&row.public_key_cbor).map_err(|_| ApiError::Internal)?;
    let policy = match row.registration_policy.as_str() {
        "preferred" => UserVerificationPolicy::Preferred,
        "discouraged" => UserVerificationPolicy::Discouraged_DO_NOT_USE,
        _ => UserVerificationPolicy::Required,
    };
    let transports = row.transports.map(|ts| {
        ts.into_iter()
            .filter_map(|t| match t.as_str() {
                "usb" => Some(AuthenticatorTransport::Usb),
                "nfc" => Some(AuthenticatorTransport::Nfc),
                "ble" => Some(AuthenticatorTransport::Ble),
                "internal" => Some(AuthenticatorTransport::Internal),
                "hybrid" => Some(AuthenticatorTransport::Hybrid),
                _ => None,
            })
            .collect()
    });
    let cred = Credential {
        cred_id: row.cred_id_raw.into(),
        cred: cose_key,
        counter: row.counter as u32,
        transports,
        user_verified: row.user_verified,
        backup_eligible: row.backup_eligible,
        backup_state: row.backup_state,
        registration_policy: policy,
        extensions: RegisteredExtensions::none(),
        attestation: ParsedAttestation::default(),
        attestation_format: AttestationFormat::None,
    };
    let passkey: Passkey = cred.into();
    let discoverable: DiscoverableKey = (&passkey).into();

    let auth_result = webauthn
        .finish_discoverable_authentication(&auth_credential, auth_state, &[discoverable])
        .map_err(|err| {
            tracing::warn!(error = ?err, "passkey login verification failed");
            ApiError::Unauthorized
        })?;

    // Update the stored counter (clone detection is handled by the library).
    let new_counter = auth_result.counter() as i64;
    sqlx::query(
        "UPDATE passkey_credentials SET counter = $1, last_used_at = now() WHERE credential_id = $2",
    )
    .bind(new_counter)
    .bind(&cred_id_b64)
    .execute(&st.pool)
    .await?;

    // Consume the challenge.
    let _: () = redis.del(&key).await?;

    let user = load_user(&st, user_id).await?;
    Ok(Json(issue_session(&st, &user).await?))
}

/// URL-safe base64 without padding, matching the credential ID encoding WebAuthn uses.
fn base64url_encode(bytes: &[u8]) -> String {
    use base64::Engine;
    base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(bytes)
}