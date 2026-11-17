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
        })
    }
}
