//! Account creation and sign-in with one-time codes, issuing JWT access tokens and rotating refresh tokens.
//!
//! Flows
//! - Sign up: `otp/send` + `otp/verify` for BOTH phone and email (purpose `signup`). Each verify returns
//!   an `otp_proof`. `POST /v1/auth/signup` takes both proofs, creates the account and returns a session.
//! - Log in: `otp/send` + `otp/verify` with purpose `login` on either channel. Verify returns a session.
//! - `refresh` rotates the refresh token; `logout` revokes it; `GET /v1/me` returns the signed-in user.
//!
//! Storage
//! - Postgres: users and refresh tokens.
//! - Redis: resend cooldowns, hourly send caps, and email code hashes (with TTL, consumed with GETDEL).
//! - Twilio Verify v2: generates, delivers and checks phone codes.

pub mod jwt;
pub mod notify;
pub mod otp;
pub mod passkey;

use axum::{
    extract::State,
    http::{header, HeaderMap, StatusCode},
    response::IntoResponse,
    routing::{get, post},
    Json, Router,
};
use chrono::{DateTime, Duration, Utc};
use garde::Validate;
use redis::AsyncCommands;
use serde::{Deserialize, Serialize};
use sqlx::FromRow;
use uuid::Uuid;

use crate::{
    error::{is_unique_violation, ApiError},
    AppState,
};

const OTP_TTL_SECS: u64 = 5 * 60;
const OTP_RESEND_SECS: u64 = 30;
const OTP_MAX_SENDS_PER_HOUR: i64 = 5;
const OTP_MAX_ATTEMPTS: i64 = 5;
const SEND_WINDOW_SECS: u64 = 60 * 60;
const REFRESH_TTL_DAYS: i64 = 30;

pub fn router() -> Router<AppState> {
    Router::new()
        .route("/v1/auth/otp/send", post(send_otp))
        .route("/v1/auth/otp/verify", post(verify_otp))
        .route("/v1/auth/signup", post(signup))
        .route("/v1/auth/refresh", post(refresh))
        .route("/v1/auth/logout", post(logout))
        .route("/v1/me", get(me))
        .merge(passkey::router())
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Channel {
    Phone,
    Email,
}

impl Channel {
    fn as_str(self) -> &'static str {
        match self {
            Channel::Phone => "phone",
            Channel::Email => "email",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Purpose {
    Signup,
    Login,
}

impl Purpose {
    fn as_str(self) -> &'static str {
        match self {
            Purpose::Signup => "signup",
            Purpose::Login => "login",
        }
    }
}

#[derive(Debug, Clone, Serialize, FromRow)]
pub struct User {
    pub id: Uuid,
    pub phone_e164: Option<String>,
    pub email: Option<String>,
    pub first_name: String,
    pub last_name: String,
    pub role: String,
    pub phone_verified_at: Option<DateTime<Utc>>,
    pub email_verified_at: Option<DateTime<Utc>>,
    pub created_at: DateTime<Utc>,
}

#[derive(Serialize)]
pub struct SessionRes {
    pub access_token: String,
    pub refresh_token: String,
    pub token_type: &'static str,
    pub expires_in: i64,
    pub user: User,
}

// ---------- validation (garde) ----------

/// Validates and canonicalises a phone number with Google's libphonenumber metadata (Rust `phonenumber` crate).
/// The number must be in international form (`+` and country code); no default region is assumed.
/// Returns the E.164 form, e.g. `+6421555123`.
pub fn canonical_phone(raw: &str) -> Option<String> {
    let number = phonenumber::parse(None, raw.trim()).ok()?;
    if !number.is_valid() {
        return None;
    }
    Some(number.format().mode(phonenumber::Mode::E164).to_string())
}

/// Lowercased, trimmed address with a local part, a dotted domain and no whitespace.
pub fn canonical_email(raw: &str) -> Option<String> {
    let email = raw.trim().to_lowercase();
    let ok = email.len() <= 254
        && email.split_once('@').is_some_and(|(local, domain)| {
            !local.is_empty()
                && !local.contains(char::is_whitespace)
                && domain.contains('.')
                && !domain.starts_with('.')
                && !domain.ends_with('.')
                && !domain.contains(char::is_whitespace)
        });
    ok.then_some(email)
}

/// garde custom rule: the destination must be valid for the channel chosen in the same request.
fn check_destination(value: &str, channel: &Channel) -> garde::Result {
    let ok = match channel {
        Channel::Phone => canonical_phone(value).is_some(),
        Channel::Email => canonical_email(value).is_some(),
    };
    if ok {
        Ok(())
    } else {
        Err(garde::Error::new(match channel {
            Channel::Phone => "invalid_phone",
            Channel::Email => "invalid_email",
        }))
    }
}

/// garde custom rule: exactly six ASCII digits.
fn check_code(value: &str, _channel: &Channel) -> garde::Result {
    if value.len() == 6 && value.bytes().all(|b| b.is_ascii_digit()) {
        Ok(())
    } else {
        Err(garde::Error::new("invalid_code"))
    }
}

/// Turns the first garde failure into a stable API error code the apps can show.
pub(crate) fn validation_error(report: garde::Report) -> ApiError {
    let code = report
        .iter()
        .next()
        .map(|(path, err)| match (path.to_string().as_str(), err.to_string().as_str()) {
            (_, "invalid_phone") => "invalid_phone",
            (_, "invalid_email") => "invalid_email",
            (_, "invalid_code") => "invalid_code",
            ("first_name" | "last_name", _) => "invalid_name",
            _ => "invalid_request",
        })
        .unwrap_or("invalid_request");
    ApiError::BadRequest(code)
}

// ---------- DB helpers ----------

async fn find_user_by(st: &AppState, channel: Channel, destination: &str) -> Result<Option<User>, ApiError> {
    let sql = match channel {
        Channel::Phone => "SELECT id, phone_e164, email, first_name, last_name, role, phone_verified_at, email_verified_at, created_at FROM users WHERE phone_e164 = $1",
        Channel::Email => "SELECT id, phone_e164, email, first_name, last_name, role, phone_verified_at, email_verified_at, created_at FROM users WHERE email = $1",
    };
    Ok(sqlx::query_as::<_, User>(sql).bind(destination).fetch_optional(&st.pool).await?)
}

pub(crate) async fn load_user(st: &AppState, id: Uuid) -> Result<User, ApiError> {
    sqlx::query_as::<_, User>(
        "SELECT id, phone_e164, email, first_name, last_name, role, phone_verified_at, email_verified_at, created_at FROM users WHERE id = $1",
    )
    .bind(id)
    .fetch_optional(&st.pool)
    .await?
    .ok_or(ApiError::Unauthorized)
}

// ---------- send ----------

#[derive(Deserialize, Validate)]
#[garde(context(Channel as ch))]
pub struct SendOtpReq {
    #[garde(skip)]
    channel: Channel,
    #[garde(custom(check_destination))]
    destination: String,
    #[garde(skip)]
    purpose: Purpose,
}

#[derive(Serialize)]
pub struct SendOtpRes {
    channel: Channel,
    destination_hint: String,
    expires_in: u64,
    resend_in: u64,
}

async fn send_otp(State(st): State<AppState>, Json(req): Json<SendOtpReq>) -> Result<Json<SendOtpRes>, ApiError> {
    req.validate_with(&req.channel).map_err(validation_error)?;
    let dest = match req.channel {
        Channel::Phone => canonical_phone(&req.destination),
        Channel::Email => canonical_email(&req.destination),
    }
    .ok_or(ApiError::BadRequest("invalid_request"))?;
    let (ch, pu) = (req.channel.as_str(), req.purpose.as_str());

    let exists = find_user_by(&st, req.channel, &dest).await?.is_some();
    match req.purpose {
        Purpose::Signup if exists => return Err(ApiError::Conflict("already_registered")),
        Purpose::Login if !exists => return Err(ApiError::NotFound("account_not_found")),
        _ => {}
    }

    let mut redis = st.redis.clone();
    let cooldown_key = format!("nova:auth:cooldown:{ch}:{pu}:{dest}");
    let hour_key = format!("nova:auth:sends:{ch}:{dest}");

    // SET NX EX is an atomic "claim the cooldown slot"; a second request inside 30s gets the remaining TTL.
    let claimed: Option<String> = redis::cmd("SET")
        .arg(&cooldown_key)
        .arg(1)
        .arg("NX")
        .arg("EX")
        .arg(OTP_RESEND_SECS)
        .query_async(&mut redis)
        .await?;
    if claimed.is_none() {
        let ttl: i64 = redis.ttl(&cooldown_key).await?;
        return Err(ApiError::TooManyRequests(ttl.max(1)));
    }

    let sent: i64 = redis.incr(&hour_key, 1).await?;
    if sent == 1 {
        let _: () = redis.expire(&hour_key, SEND_WINDOW_SECS as i64).await?;
    }
    if sent > OTP_MAX_SENDS_PER_HOUR {
        let ttl: i64 = redis.ttl(&hour_key).await?;
        // Release the cooldown we just claimed, the hourly cap is what blocks this request.
        let _: () = redis.del(&cooldown_key).await?;
        return Err(ApiError::TooManyRequests(ttl.max(1)));
    }

    let delivery = match req.channel {
        Channel::Phone => notify::send_phone_verification(&st, &dest).await,
        Channel::Email => {
            let code = otp::generate_code();
            let delivered = notify::send_email_otp(&st, &dest, &code).await;
            if delivered.is_ok() {
                let hash = otp::hash_code(&st.cfg.otp_secret, ch, &dest, pu, &code);
                let _: () = redis.set_ex(email_code_key(pu, &dest), hash, OTP_TTL_SECS).await?;
                let _: () = redis.del(email_attempts_key(pu, &dest)).await?;
            }
            delivered
        }
    };
    if let Err(err) = delivery {
        // Nothing reached the user, so let them retry straight away.
        let _: () = redis.del(&cooldown_key).await?;
        return Err(err);
    }

    Ok(Json(SendOtpRes {
        channel: req.channel,
        destination_hint: otp::mask(ch, &dest),
        expires_in: OTP_TTL_SECS,
        resend_in: OTP_RESEND_SECS,
    }))
}

fn email_code_key(purpose: &str, dest: &str) -> String {
    format!("nova:auth:code:email:{purpose}:{dest}")
}

fn email_attempts_key(purpose: &str, dest: &str) -> String {
    format!("nova:auth:attempts:email:{purpose}:{dest}")
}

// ---------- verify ----------

#[derive(Deserialize, Validate)]
#[garde(context(Channel as ch))]
pub struct VerifyOtpReq {
    #[garde(skip)]
    channel: Channel,
    #[garde(custom(check_destination))]
    destination: String,
    #[garde(skip)]
    purpose: Purpose,
    #[garde(custom(check_code))]
    code: String,
}

#[derive(Serialize)]
#[serde(untagged)]
pub enum VerifyRes {
    /// Sign-up: one proof per channel, passed to `POST /v1/auth/signup`.
    Proof { otp_proof: String, channel: Channel, destination: String },
    /// Login: a signed-in session.
    Session(SessionRes),
}

async fn verify_otp(State(st): State<AppState>, Json(req): Json<VerifyOtpReq>) -> Result<Json<VerifyRes>, ApiError> {
    req.validate_with(&req.channel).map_err(validation_error)?;
    let dest = match req.channel {
        Channel::Phone => canonical_phone(&req.destination),
        Channel::Email => canonical_email(&req.destination),
    }
    .ok_or(ApiError::BadRequest("invalid_request"))?;
    let (ch, pu) = (req.channel.as_str(), req.purpose.as_str());

    match req.channel {
        Channel::Phone => {
            if !notify::check_phone_verification(&st, &dest, &req.code).await? {
                return Err(ApiError::BadRequest("invalid_code"));
            }
        }
        Channel::Email => {
            let mut redis = st.redis.clone();
            let code_key = email_code_key(pu, &dest);
            let attempts_key = email_attempts_key(pu, &dest);

            let attempts: i64 = redis.get::<_, Option<i64>>(&attempts_key).await?.unwrap_or(0);
            if attempts >= OTP_MAX_ATTEMPTS {
                return Err(ApiError::BadRequest("too_many_attempts"));
            }
            let stored: Option<String> = redis.get(&code_key).await?;
            let stored = stored.ok_or(ApiError::BadRequest("code_not_found"))?;

            if !otp::verify_code(&st.cfg.otp_secret, ch, &dest, pu, &req.code, &stored) {
                let _: i64 = redis.incr(&attempts_key, 1).await?;
                let _: () = redis.expire(&attempts_key, OTP_TTL_SECS as i64).await?;
                return Err(ApiError::BadRequest("invalid_code"));
            }

            // GETDEL consumes the code atomically, so two concurrent correct submissions cannot both win.
            let consumed: Option<String> = redis.get_del(&code_key).await?;
            if consumed.is_none() {
                return Err(ApiError::BadRequest("code_not_found"));
            }
            let _: () = redis.del(&attempts_key).await?;
        }
    }

    match req.purpose {
        Purpose::Signup => {
            if find_user_by(&st, req.channel, &dest).await?.is_some() {
                return Err(ApiError::Conflict("already_registered"));
            }
            let otp_proof = jwt::encode_proof(&st.cfg.jwt_secret, ch, &dest)?;
            Ok(Json(VerifyRes::Proof { otp_proof, channel: req.channel, destination: dest }))
        }
        Purpose::Login => {
            let user = find_user_by(&st, req.channel, &dest)
                .await?
                .ok_or(ApiError::NotFound("account_not_found"))?;
            Ok(Json(VerifyRes::Session(issue_session(&st, &user).await?)))
        }
    }
}

// ---------- signup ----------

#[derive(Deserialize, Validate)]
pub struct SignupReq {
    #[garde(length(min = 1, max = 60))]
    first_name: String,
    #[garde(length(max = 60))]
    #[serde(default)]
    last_name: String,
    #[garde(skip)]
    phone_proof: Option<String>,
    #[garde(skip)]
    email_proof: Option<String>,
    #[garde(skip)]
    phone: Option<String>,
    #[garde(skip)]
    email: Option<String>,
}

async fn signup(State(st): State<AppState>, Json(req): Json<SignupReq>) -> Result<(StatusCode, Json<SessionRes>), ApiError> {
    req.validate().map_err(validation_error)?;
    let first_name = req.first_name.trim().to_string();
    let last_name = req.last_name.trim().to_string();
    if first_name.is_empty() {
        return Err(ApiError::BadRequest("invalid_name"));
    }

    let mut phone_e164: Option<String> = None;
    let mut phone_verified = false;
    if let Some(proof) = req.phone_proof.as_deref().filter(|s| !s.is_empty()) {
        let claims = jwt::decode_typed(&st.cfg.jwt_secret, proof, jwt::TYP_PROOF)?;
        if claims.channel.as_deref() != Some("phone") {
            return Err(ApiError::BadRequest("proof_mismatch"));
        }
        phone_e164 = Some(claims.dest.ok_or(ApiError::Unauthorized)?);
        phone_verified = true;
    } else if let Some(raw_phone) = req.phone.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
        phone_e164 = Some(canonical_phone(raw_phone).ok_or(ApiError::BadRequest("invalid_phone"))?);
    }

    let mut email_addr: Option<String> = None;
    let mut email_verified = false;
    if let Some(proof) = req.email_proof.as_deref().filter(|s| !s.is_empty()) {
        let claims = jwt::decode_typed(&st.cfg.jwt_secret, proof, jwt::TYP_PROOF)?;
        if claims.channel.as_deref() != Some("email") {
            return Err(ApiError::BadRequest("proof_mismatch"));
        }
        email_addr = Some(claims.dest.ok_or(ApiError::Unauthorized)?);
        email_verified = true;
    } else if let Some(raw_email) = req.email.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
        email_addr = Some(canonical_email(raw_email).ok_or(ApiError::BadRequest("invalid_email"))?);
    }

    if !phone_verified && !email_verified {
        return Err(ApiError::BadRequest("proof_mismatch"));
    }

    let id = Uuid::now_v7();
    let inserted = sqlx::query(
        "INSERT INTO users (id, phone_e164, email, first_name, last_name, phone_verified_at, email_verified_at)
         VALUES ($1, $2, $3, $4, $5, CASE WHEN $6 THEN now() ELSE NULL END, CASE WHEN $7 THEN now() ELSE NULL END)",
    )
    .bind(id)
    .bind(&phone_e164)
    .bind(&email_addr)
    .bind(&first_name)
    .bind(&last_name)
    .bind(phone_verified)
    .bind(email_verified)
    .execute(&st.pool)
    .await;
    match inserted {
        Ok(_) => {}
        Err(err) if is_unique_violation(&err) => return Err(ApiError::Conflict("already_registered")),
        Err(err) => return Err(err.into()),
    }

    let user = load_user(&st, id).await?;
    Ok((StatusCode::CREATED, Json(issue_session(&st, &user).await?)))
}

// ---------- sessions ----------

pub(crate) async fn issue_session(st: &AppState, user: &User) -> Result<SessionRes, ApiError> {
    let access_token = jwt::encode_access(&st.cfg.jwt_secret, user.id)?;
    let refresh_token = otp::new_refresh_token();
    sqlx::query("INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at) VALUES ($1, $2, $3, $4)")
        .bind(Uuid::now_v7())
        .bind(user.id)
        .bind(otp::sha256_hex(&refresh_token))
        .bind(Utc::now() + Duration::days(REFRESH_TTL_DAYS))
        .execute(&st.pool)
        .await?;
    // Signing in again within the 30-day grace period cancels a pending account deletion
    // (deletion revokes every refresh token, so only a fresh OTP/passkey sign-in gets here).
    sqlx::query("UPDATE users SET deletion_requested_at = NULL WHERE id = $1 AND deletion_requested_at IS NOT NULL")
        .bind(user.id)
        .execute(&st.pool)
        .await?;
    Ok(SessionRes {
        access_token,
        refresh_token,
        token_type: "Bearer",
        expires_in: jwt::ACCESS_TTL_SECS,
        user: user.clone(),
    })
}

#[derive(Deserialize, Validate)]
pub struct RefreshReq {
    #[garde(length(min = 32, max = 256))]
    refresh_token: String,
}

#[derive(FromRow)]
struct RefreshRow {
    id: Uuid,
    user_id: Uuid,
    expires_at: DateTime<Utc>,
    revoked_at: Option<DateTime<Utc>>,
}

async fn refresh(State(st): State<AppState>, Json(req): Json<RefreshReq>) -> Result<Json<SessionRes>, ApiError> {
    req.validate().map_err(validation_error)?;
    let row: RefreshRow = sqlx::query_as(
        "SELECT id, user_id, expires_at, revoked_at FROM refresh_tokens WHERE token_hash = $1",
    )
    .bind(otp::sha256_hex(&req.refresh_token))
    .fetch_optional(&st.pool)
    .await?
    .ok_or(ApiError::Unauthorized)?;

    if row.revoked_at.is_some() {
        // A rotated token was replayed: assume theft and end every session for this user.
        sqlx::query("UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = $1 AND revoked_at IS NULL")
            .bind(row.user_id)
            .execute(&st.pool)
            .await?;
        return Err(ApiError::Unauthorized);
    }
    if row.expires_at < Utc::now() {
        return Err(ApiError::Unauthorized);
    }

    let rotated = sqlx::query("UPDATE refresh_tokens SET revoked_at = now() WHERE id = $1 AND revoked_at IS NULL")
        .bind(row.id)
        .execute(&st.pool)
        .await?
        .rows_affected();
    if rotated != 1 {
        return Err(ApiError::Unauthorized);
    }

    let user = load_user(&st, row.user_id).await?;
    Ok(Json(issue_session(&st, &user).await?))
}

#[derive(Deserialize, Validate)]
pub struct LogoutReq {
    #[garde(length(min = 32, max = 256))]
    refresh_token: String,
}

async fn logout(State(st): State<AppState>, Json(req): Json<LogoutReq>) -> Result<StatusCode, ApiError> {
    req.validate().map_err(validation_error)?;
    sqlx::query("UPDATE refresh_tokens SET revoked_at = now() WHERE token_hash = $1 AND revoked_at IS NULL")
        .bind(otp::sha256_hex(&req.refresh_token))
        .execute(&st.pool)
        .await?;
    Ok(StatusCode::NO_CONTENT)
}

pub(crate) fn require_user_id(st: &AppState, headers: &HeaderMap) -> Result<Uuid, ApiError> {
    let token = headers
        .get(header::AUTHORIZATION)
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "))
        .ok_or(ApiError::Unauthorized)?;
    let claims = jwt::decode_typed(&st.cfg.jwt_secret, token, jwt::TYP_ACCESS)?;
    Uuid::parse_str(&claims.sub).map_err(|_| ApiError::Unauthorized)
}

/// Contact phone for a user (driver <-> rider during a trip). Only verified numbers.
pub(crate) async fn verified_phone(st: &AppState, user: Uuid) -> Option<String> {
    sqlx::query_scalar("SELECT phone_e164 FROM users WHERE id = $1 AND phone_verified_at IS NOT NULL")
        .bind(user)
        .fetch_optional(&st.pool)
        .await
        .ok()
        .flatten()
}

async fn me(State(st): State<AppState>, headers: HeaderMap) -> Result<impl IntoResponse, ApiError> {
    let user_id = require_user_id(&st, &headers)?;
    Ok(Json(load_user(&st, user_id).await?))
}
