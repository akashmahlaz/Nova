use std::env;

/// Runtime configuration, read from environment variables (or `server/.env` in development).
#[derive(Clone)]
pub struct Config {
    pub database_url: String,
    pub redis_url: String,
    pub bind_addr: String,
    /// Signs access and OTP-proof JWTs (HS256). At least 32 bytes.
    pub jwt_secret: String,
    /// Keys the HMAC that hashes OTP codes at rest. At least 32 bytes.
    pub otp_secret: String,
    pub twilio: Option<TwilioConfig>,
    pub resend: Option<ResendConfig>,
    /// Development only: skip Twilio/Resend and log the OTP instead. Never enable in production.
    pub otp_dev_log: bool,
    /// WebAuthn RP ID — the domain the passkey is bound to (e.g. `nova.punjab.fireclaw.in`).
    pub passkey_rp_id: String,
    /// WebAuthn RP origin — the full origin (e.g. `https://nova.punjab.fireclaw.in`).
    pub passkey_rp_origin: String,
    /// SHA-256 of the signing certificate, for `/.well-known/assetlinks.json`.
    pub passkey_assetlinks_fingerprint: String,
    /// Server-side Google Maps Platform key (Places API (New), Routes API, Geocoding API).
    /// Keep it separate from the Android key and restrict it to this server's IP + those 3 APIs.
    /// `None` makes the /v1/geo and /v1/rides/quote endpoints return 503 `maps_unavailable`.
    pub google_maps_api_key: Option<String>,
    /// Public web origin for shareable links (ride tracking: `{public_web_url}/track/<token>`).
    /// Must match the App Link host in the Android manifest so links open the app when installed.
    pub public_web_url: String,
    /// Development only: new driver applications are approved at once instead of waiting for
    /// an admin (`PATCH /v1/admin/drivers/{id}`). Never enable in production.
    pub driver_auto_approve: bool,
    /// Private AWS S3 bucket for signed media uploads/downloads across Rider, Driver, and Admin.
    pub s3: Option<S3Config>,
}

#[derive(Clone)]
pub struct S3Config {
    pub bucket: String,
    pub region: String,
    pub access_key_id: String,
    pub secret_access_key: String,
}

#[derive(Clone)]
pub struct TwilioConfig {
    pub account_sid: String,
    pub auth_token: String,
    pub service_sid: String,
}

#[derive(Clone)]
pub struct ResendConfig {
    pub api_key: String,
    pub from: String,
}

impl Config {
    pub fn from_env() -> Result<Self, String> {
        let required = |key: &str| env::var(key).map_err(|_| format!("missing env var {key}"));
        let optional = |key: &str| env::var(key).ok().filter(|v| !v.trim().is_empty());

        let jwt_secret = required("JWT_SECRET")?;
        if jwt_secret.len() < 32 {
            return Err("JWT_SECRET must be at least 32 bytes".into());
        }
        let otp_secret = optional("OTP_HMAC_SECRET")
            .unwrap_or_else(|| format!("{jwt_secret}_nova_otp_hmac_default_salt"));
        if otp_secret.len() < 32 {
            return Err("OTP_HMAC_SECRET must be at least 32 bytes".into());
        }

        let twilio = match (
            optional("TWILIO_ACCOUNT_SID"),
            optional("TWILIO_AUTH_TOKEN"),
            optional("TWILIO_VERIFY_SERVICE_SID"),
        ) {
            (Some(account_sid), Some(auth_token), Some(service_sid)) => {
                Some(TwilioConfig { account_sid, auth_token, service_sid })
            }
            _ => None,
        };
        let resend = match (
            optional("RESEND_API_KEY"),
            optional("RESEND_FROM").or_else(|| Some("Nova <auth@novataxis.co.nz>".into())),
        ) {
            (Some(api_key), Some(from)) => Some(ResendConfig { api_key, from }),
            _ => None,
        };

        Ok(Self {
            database_url: required("DATABASE_URL")?,
            redis_url: optional("REDIS_URL").unwrap_or_else(|| "redis://127.0.0.1:6379".into()),
            // 0.0.0.0 so a physical phone on the same Wi-Fi can reach the dev server. Use 127.0.0.1 to keep it local.
            bind_addr: optional("BIND_ADDR").unwrap_or_else(|| "0.0.0.0:3000".into()),
            jwt_secret,
            otp_secret,
            twilio,
            resend,
            otp_dev_log: optional("OTP_DEV_LOG").is_some_and(|v| v == "true"),
            passkey_rp_id: optional("PASSKEY_RP_ID").unwrap_or_else(|| "localhost".into()),
            passkey_rp_origin: optional("PASSKEY_RP_ORIGIN")
                .unwrap_or_else(|| "http://localhost:3000".into()),
            passkey_assetlinks_fingerprint: optional("PASSKEY_ASSETLINKS_FINGERPRINT")
                .unwrap_or_default(),
            google_maps_api_key: optional("GOOGLE_MAPS_API_KEY"),
            public_web_url: optional("PUBLIC_WEB_URL")
                .unwrap_or_else(|| "https://nova-web-production.punjab.fireclaw.in".into())
                .trim_end_matches('/')
                .to_string(),
            driver_auto_approve: optional("DRIVER_AUTO_APPROVE").is_some_and(|v| v == "true"),
            s3: match (
                optional("AWS_S3_BUCKET"),
                optional("AWS_REGION"),
                optional("AWS_ACCESS_KEY_ID"),
                optional("AWS_SECRET_ACCESS_KEY"),
            ) {
                (Some(bucket), Some(region), Some(access_key_id), Some(secret_access_key)) => {
                    Some(S3Config { bucket, region, access_key_id, secret_access_key })
                }
                _ => None,
            },
        })
    }
}
