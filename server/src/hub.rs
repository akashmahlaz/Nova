//! Live channel: one WebSocket per signed-in app, used to push events the moment they happen
//! (ride offers to drivers, ride status to riders; chat and calls later).
//!
//! `GET /v1/ws` with `Authorization: Bearer <access token>` (header only, so tokens never land in
//! URL logs). The server only pushes; the apps act through the normal HTTP endpoints, which keeps
//! every write validated and idempotent in one place. If the socket drops, nothing is lost: each
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

use crate::{auth::require_user_id, error::ApiError, AppState};

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
    ping.tick().await;
    if socket.send(Message::Text(json!({ "type": "hello" }).to_string().into())).await.is_ok() {
        loop {
            tokio::select! {
                event = outbox.recv() => match event {
                    Some(text) => if socket.send(Message::Text(text.into())).await.is_err() { break },
                    None => break,
                },
                incoming = socket.recv() => match incoming {
                    // The apps don't send anything yet; reads only notice a close.
                    Some(Ok(Message::Close(_))) | Some(Err(_)) | None => break,
                    Some(Ok(_)) => {}
                },
                _ = ping.tick() => if socket.send(Message::Ping(Default::default())).await.is_err() { break },
            }
        }
    }
    st.hub.remove(user, id);
    tracing::debug!(%user, "live socket closed");
}
