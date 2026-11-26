//! In-trip chat between the rider and the driver, like Uber's in-app messages.
//!
//! `GET  /v1/rides/{id}/messages` lists the conversation and marks the other side's messages read.
//! `POST /v1/rides/{id}/messages` with `{"text": "..."}` sends one.
//!
//! Both apps use the same routes: the server works out from the ride whether the caller is its
//! rider (or the passenger it was booked for) or its driver. Sending is open while a driver is
//! assigned and the trip isn't over; reading stays open afterwards. Each new message is pushed to
//! the other side over the live socket as
//! `{"type":"chat_message","ride_id","id","text","from":"rider"|"driver","sent_at"}`.

use axum::{
    extract::{Path, State},
    http::HeaderMap,
    routing::get,
    Json, Router,
};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use serde_json::json;
use sqlx::FromRow;
use uuid::Uuid;

use crate::{auth::require_user_id, error::ApiError, geo::rate_limit, AppState};

pub fn router() -> Router<AppState> {
    Router::new().route("/v1/rides/{id}/messages", get(list).post(send))
}

#[derive(Serialize, FromRow)]
pub struct Message {
    id: Uuid,
    /// "rider" or "driver".
    from: String,
    text: String,
    sent_at: DateTime<Utc>,
    read_at: Option<DateTime<Utc>>,
}

struct Party {
    role: &'static str,
    status: String,
    rider: Uuid,
    passenger: Option<Uuid>,
    driver: Option<Uuid>,
}

/// Which side of this ride the caller is on, or not found (so ride ids can't be probed).
async fn party(st: &AppState, user: Uuid, ride: Uuid) -> Result<Party, ApiError> {
    let row: Option<(String, Uuid, Option<Uuid>, Option<Uuid>)> =
        sqlx::query_as("SELECT status, rider_id, passenger_user_id, driver_id FROM rides WHERE id = $1")
            .bind(ride)
            .fetch_optional(&st.pool)
            .await?;
    let Some((status, rider, passenger, driver)) = row else {
        return Err(ApiError::NotFound("ride_not_found"));
    };
    let role = if driver == Some(user) {
        "driver"
    } else if rider == user || passenger == Some(user) {
        "rider"
    } else {
        return Err(ApiError::NotFound("ride_not_found"));
    };
    Ok(Party { role, status, rider, passenger, driver })
}

async fn list(State(st): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>) -> Result<Json<Vec<Message>>, ApiError> {
    let user = require_user_id(&st, &headers)?;
    let me = party(&st, user, id).await?;
    sqlx::query("UPDATE ride_messages SET read_at = now() WHERE ride_id = $1 AND sender_role <> $2 AND read_at IS NULL")
        .bind(id)
        .bind(me.role)
        .execute(&st.pool)
        .await?;
    let rows = sqlx::query_as::<_, Message>(
        "SELECT id, sender_role AS \"from\", body AS text, created_at AS sent_at, read_at
         FROM ride_messages WHERE ride_id = $1 ORDER BY created_at",
    )
    .bind(id)
    .fetch_all(&st.pool)
    .await?;
    Ok(Json(rows))
}

#[derive(Deserialize)]
pub struct SendReq {
    text: String,
}

async fn send(State(st): State<AppState>, headers: HeaderMap, Path(id): Path<Uuid>, Json(req): Json<SendReq>) -> Result<Json<Message>, ApiError> {
    let user = require_user_id(&st, &headers)?;
    rate_limit(&st, "ride_chat", user, 30, 60).await?;
    let me = party(&st, user, id).await?;
    if !matches!(me.status.as_str(), "accepted" | "arriving" | "in_progress") {
        return Err(ApiError::Conflict("chat_closed"));
    }
    let text: String = req.text.trim().chars().take(1000).collect();
    if text.is_empty() {
        return Err(ApiError::BadRequest("empty_message"));
    }
    let msg = sqlx::query_as::<_, Message>(
        "INSERT INTO ride_messages (id, ride_id, sender_id, sender_role, body) VALUES ($1, $2, $3, $4, $5)
         RETURNING id, sender_role AS \"from\", body AS text, created_at AS sent_at, read_at",
    )
    .bind(Uuid::now_v7())
    .bind(id)
    .bind(user)
    .bind(me.role)
    .bind(&text)
    .fetch_one(&st.pool)
    .await?;
    let event = json!({
        "type": "chat_message", "ride_id": id, "id": msg.id, "text": msg.text, "from": me.role, "sent_at": msg.sent_at,
    });
    let to: Vec<Uuid> = if me.role == "driver" {
        std::iter::once(me.rider).chain(me.passenger).collect()
    } else {
        me.driver.into_iter().collect()
    };
    for other in to {
        st.hub.send(other, event.clone());
    }
    Ok(Json(msg))
}
