//! Live channel: one WebSocket per signed-in app, used to push events the moment they happen
//! (ride offers to drivers, ride status and driver location to riders, chat messages; see chat.rs).
//!
//! `GET /v1/ws` with `Authorization: Bearer <access token>` (header only, so tokens never land in
//! URL logs). The server pushes events; the one thing apps send is the driver's GPS, as
//! `{"type":"location","lat":..,"lng":..}` every few seconds, which is cheaper than an HTTP
//! request per fix. Every other action goes through the normal HTTP endpoints, which keeps
//! writes validated and idempotent in one place. Riders get `driver_location` events back. If the socket drops, nothing is lost: each
//! app re-reads its state over HTTP when it reconnects (the server sends `hello` on connect).
//!
//! Connections live in this process's memory. Running several API instances needs a Redis
//! pub/sub fan-out in [Hub::send]; one instance doesn't.

use std::{
    sync::{
        atomic::{AtomicU64, Ordering},
        Arc,
    },
    time::Duration,
};

use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        State,
    },
    http::HeaderMap,
    response::Response,
    routing::get,
    Router,
};
use dashmap::DashMap;
use serde_json::{json, Value};
use tokio::sync::mpsc;
use uuid::Uuid;

use crate::{auth::require_user_id, drivers, error::ApiError, AppState};

/// What an app may send over the socket.
#[derive(serde::Deserialize)]
struct Inbound {
    #[serde(rename = "type")]
    kind: String,
    lat: Option<f64>,
    lng: Option<f64>,
    heading: Option<f64>,
    speed: Option<f64>,
}

/// Pings keep mobile networks and proxies from closing an idle socket.
const PING_EVERY: Duration = Duration::from_secs(25);
/// A phone can have a few sockets open briefly while reconnecting; more than this is a bug or abuse.
const MAX_SOCKETS_PER_USER: usize = 4;

type Sender = mpsc::UnboundedSender<String>;

#[derive(Clone, Default)]
pub struct Hub(Arc<Inner>);

#[derive(Default)]
struct Inner {
    conns: DashMap<Uuid, Vec<(u64, Sender)>>,
    next_id: AtomicU64,
}

impl Hub {
    /// Pushes an event to every open socket of `user`. Returns whether any socket took it.
    pub fn send(&self, user: Uuid, event: Value) -> bool {
        let text = event.to_string();
        let delivered = match self.0.conns.get_mut(&user) {
            Some(mut senders) => {
                senders.retain(|(_, tx)| tx.send(text.clone()).is_ok());
                !senders.is_empty()
            }
            None => false,
        };
        if !delivered {
            self.0.conns.remove_if(&user, |_, senders| senders.is_empty());
        }
        delivered
    }

    fn add(&self, user: Uuid) -> (u64, mpsc::UnboundedReceiver<String>) {
        let (tx, rx) = mpsc::unbounded_channel();
        let id = self.0.next_id.fetch_add(1, Ordering::Relaxed);
        let mut senders = self.0.conns.entry(user).or_default();
        if senders.len() >= MAX_SOCKETS_PER_USER {
            senders.remove(0); // the oldest socket is the stale one
        }
        senders.push((id, tx));
        (id, rx)
    }

    fn remove(&self, user: Uuid, id: u64) {
        if let Some(mut senders) = self.0.conns.get_mut(&user) {
            senders.retain(|(sid, _)| *sid != id);
        }
        self.0.conns.remove_if(&user, |_, senders| senders.is_empty());
    }
}

pub fn router() -> Router<AppState> {
    Router::new().route("/v1/ws", get(connect))
}

async fn connect(State(st): State<AppState>, headers: HeaderMap, upgrade: WebSocketUpgrade) -> Result<Response, ApiError> {
    let user = require_user_id(&st, &headers)?;
    Ok(upgrade.on_upgrade(move |socket| serve(st, user, socket)))
}

async fn serve(st: AppState, user: Uuid, mut socket: WebSocket) {
    let (id, mut outbox) = st.hub.add(user);
    tracing::debug!(%user, "live socket open");
    let mut ping = tokio::time::interval(PING_EVERY);
    // Checked on the first location ping, so rider sockets never pay for the lookup.
    let mut is_driver: Option<bool> = None;
    ping.tick().await;
    if socket.send(Message::Text(json!({ "type": "hello" }).to_string().into())).await.is_ok() {
        loop {
            tokio::select! {
                event = outbox.recv() => match event {
                    Some(text) => if socket.send(Message::Text(text.into())).await.is_err() { break },
                    None => break,
                },
                incoming = socket.recv() => match incoming {
                    Some(Ok(Message::Close(_))) | Some(Err(_)) | None => break,
                    Some(Ok(Message::Text(text))) => {
                        if let Some(reply) = handle(&st, user, &mut is_driver, &text).await {
                            if socket.send(Message::Text(reply.to_string().into())).await.is_err() { break }
                        }
                    }
                    Some(Ok(_)) => {}
                },
                _ = ping.tick() => if socket.send(Message::Ping(Default::default())).await.is_err() { break },
            }
        }
    }
    st.hub.remove(user, id);
    tracing::debug!(%user, "live socket closed");
}

/// Handles one message from an app. Replies only with errors, so a ping costs no downstream bytes.
async fn handle(st: &AppState, user: Uuid, is_driver: &mut Option<bool>, text: &str) -> Option<Value> {
    let msg: Inbound = serde_json::from_str(text).ok()?;
    if msg.kind != "location" {
        return None;
    }
    if is_driver.is_none() {
        let role: Option<String> = sqlx::query_scalar("SELECT role FROM users WHERE id = $1")
            .bind(user)
            .fetch_optional(&st.pool)
            .await
            .ok()
            .flatten();
        *is_driver = Some(role.as_deref() == Some("driver"));
    }
    if *is_driver != Some(true) {
        return Some(json!({ "type": "error", "code": "not_driver_account" }));
    }
    let (Some(lat), Some(lng)) = (msg.lat, msg.lng) else {
        return Some(json!({ "type": "error", "code": "invalid_location" }));
    };
    match drivers::ingest_location(st, user, drivers::LatLng { lat, lng, heading: msg.heading, speed: msg.speed }).await {
        Ok(_) => None,
        Err(err) => Some(json!({ "type": "error", "code": err.code() })),
    }
}
