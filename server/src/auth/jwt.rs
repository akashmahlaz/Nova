use chrono::Utc;
use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::error::ApiError;

pub const TYP_ACCESS: &str = "access";
pub const TYP_PROOF: &str = "otp_proof";

pub const ACCESS_TTL_SECS: i64 = 15 * 60;
pub const PROOF_TTL_SECS: i64 = 15 * 60;

#[derive(Debug, Serialize, Deserialize)]
pub struct Claims {
    /// User id for access tokens, empty for OTP proofs.
    pub sub: String,
    /// `access` or `otp_proof`. A token is only accepted for the purpose it was issued for.
    pub typ: String,
    pub iat: i64,
    pub exp: i64,
    /// OTP proofs only: the verified channel (`phone`/`email`) and destination.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub channel: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub dest: Option<String>,
}

fn sign(secret: &str, claims: &Claims) -> Result<String, ApiError> {
    encode(&Header::new(Algorithm::HS256), claims, &EncodingKey::from_secret(secret.as_bytes())).map_err(|err| {
        tracing::error!(error = ?err, "jwt encode failed");
        ApiError::Internal
    })
}

pub fn encode_access(secret: &str, user_id: Uuid) -> Result<String, ApiError> {
    let now = Utc::now().timestamp();
    sign(
        secret,
        &Claims {
            sub: user_id.to_string(),
            typ: TYP_ACCESS.into(),
            iat: now,
            exp: now + ACCESS_TTL_SECS,
            channel: None,
            dest: None,
        },
    )
}

pub fn encode_proof(secret: &str, channel: &str, destination: &str) -> Result<String, ApiError> {
    let now = Utc::now().timestamp();
    sign(
        secret,
        &Claims {
            sub: String::new(),
            typ: TYP_PROOF.into(),
            iat: now,
            exp: now + PROOF_TTL_SECS,
            channel: Some(channel.into()),
            dest: Some(destination.into()),
        },
    )
}

/// Verifies signature, expiry and token type. Any failure is a plain 401.
pub fn decode_typed(secret: &str, token: &str, typ: &str) -> Result<Claims, ApiError> {
    let validation = Validation::new(Algorithm::HS256);
    let data = decode::<Claims>(token, &DecodingKey::from_secret(secret.as_bytes()), &validation)
        .map_err(|_| ApiError::Unauthorized)?;
    if data.claims.typ != typ {
        return Err(ApiError::Unauthorized);
    }
    Ok(data.claims)
}
