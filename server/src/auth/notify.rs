//! Delivers one-time codes (phone through Twilio Verify v2, email through Resend) and ride texts
//! (Twilio Programmable Messaging, see [send_text]).

use serde_json::json;

use crate::{error::ApiError, AppState};

pub async fn send_phone_verification(st: &AppState, to: &str) -> Result<(), ApiError> {
    if st.cfg.otp_dev_log {
        tracing::warn!(to, "DEV OTP (Twilio Verify bypassed, OTP_DEV_LOG=true, use '123456')");
        return Ok(());
    }
    let twilio = st.cfg.twilio.as_ref().ok_or(ApiError::Unavailable("sms_not_configured"))?;
    let url = format!(
        "https://verify.twilio.com/v2/Services/{}/Verifications",
        twilio.service_sid
    );
    let resp = st
        .http
        .post(url)
        .basic_auth(&twilio.account_sid, Some(&twilio.auth_token))
        .form(&[("To", to), ("Channel", "sms")])
        .send()
        .await
        .map_err(|err| {
            tracing::error!(error = ?err, "twilio verify request failed");
            ApiError::Unavailable("sms_failed")
        })?;

    if !resp.status().is_success() {
        let status = resp.status();
        let text = resp.text().await.unwrap_or_default();
        tracing::error!(%status, body = %text, "twilio verify rejected request");
        return Err(ApiError::Unavailable("sms_failed"));
    }
    Ok(())
}

pub async fn check_phone_verification(st: &AppState, to: &str, code: &str) -> Result<bool, ApiError> {
    if st.cfg.otp_dev_log && code == "123456" {
        tracing::warn!(to, "DEV OTP check approved for '123456'");
        return Ok(true);
    }
    let twilio = st.cfg.twilio.as_ref().ok_or(ApiError::Unavailable("sms_not_configured"))?;
    let url = format!(
        "https://verify.twilio.com/v2/Services/{}/VerificationCheck",
        twilio.service_sid
    );
    let resp = st
        .http
        .post(url)
        .basic_auth(&twilio.account_sid, Some(&twilio.auth_token))
        .form(&[("To", to), ("Code", code)])
        .send()
        .await
        .map_err(|err| {
            tracing::error!(error = ?err, "twilio verification check failed");
            ApiError::Unavailable("sms_failed")
        })?;

    if resp.status() == reqwest::StatusCode::NOT_FOUND {
        return Ok(false);
    }

    if !resp.status().is_success() {
        let status = resp.status();
        let text = resp.text().await.unwrap_or_default();
        tracing::error!(%status, body = %text, "twilio verification check error");
        return Err(ApiError::Unavailable("sms_failed"));
    }

    let body: serde_json::Value = resp.json().await.map_err(|err| {
        tracing::error!(error = ?err, "failed to parse twilio verification check response");
        ApiError::Internal
    })?;

    let approved = body.get("status").and_then(|v| v.as_str()) == Some("approved")
        && body.get("valid").and_then(|v| v.as_bool()) == Some(true);

    Ok(approved)
}

pub async fn send_email_otp(st: &AppState, to: &str, code: &str) -> Result<(), ApiError> {
    if st.cfg.otp_dev_log {
        tracing::warn!(to, code, "DEV OTP (email not sent, OTP_DEV_LOG=true)");
        return Ok(());
    }
    let resend = st.cfg.resend.as_ref().ok_or(ApiError::Unavailable("email_not_configured"))?;
    let payload = json!({
        "from": resend.from,
        "to": [to],
        "subject": "Your Nova code",
        "text": format!(
            "Your Nova code is {code}.\n\nIt expires in 5 minutes. If you did not request it, you can ignore this email."
        ),
    });
    let resp = st
        .http
        .post("https://api.resend.com/emails")
        .bearer_auth(&resend.api_key)
        .json(&payload)
        .send()
        .await
        .map_err(|err| {
            tracing::error!(error = ?err, "resend request failed");
            ApiError::Unavailable("email_failed")
        })?;
    if !resp.status().is_success() {
        let status = resp.status();
        let text = resp.text().await.unwrap_or_default();
        tracing::error!(%status, body = %text, "resend rejected email");
        return Err(ApiError::Unavailable("email_failed"));
    }
    Ok(())
}

/// Sends a plain SMS through a Twilio Messaging Service (TWILIO_MESSAGING_SERVICE_SID). Used for
/// ride texts to someone a rider booked for. Returns false when it was not sent (not configured,
/// dev mode, or Twilio refused); callers treat texts as best effort.
pub async fn send_text(st: &AppState, to: &str, body: &str) -> bool {
    let (Some(twilio), Some(service)) = (st.cfg.twilio.as_ref(), st.cfg.twilio_messaging_service_sid.as_deref()) else {
        tracing::info!(to, body, "ride text not sent: TWILIO_MESSAGING_SERVICE_SID not configured");
        return false;
    };
    if st.cfg.otp_dev_log {
        tracing::warn!(to, body, "DEV ride text (not sent, OTP_DEV_LOG=true)");
        return false;
    }
    let url = format!("https://api.twilio.com/2010-04-01/Accounts/{}/Messages.json", twilio.account_sid);
    let resp = st
        .http
        .post(url)
        .basic_auth(&twilio.account_sid, Some(&twilio.auth_token))
        .form(&[("To", to), ("MessagingServiceSid", service), ("Body", body)])
        .send()
        .await;
    match resp {
        Ok(r) if r.status().is_success() => true,
        Ok(r) => {
            let status = r.status();
            let text = r.text().await.unwrap_or_default();
            tracing::error!(%status, body = %text, "twilio message rejected");
            false
        }
        Err(err) => {
            tracing::error!(error = ?err, "twilio message request failed");
            false
        }
    }
}
