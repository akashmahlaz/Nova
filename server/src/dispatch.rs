//! Dispatch: find a driver for every requested ride.
//!
//! One lightweight task per requested ride (spawned when it's booked, when a scheduled ride comes
//! due, when a driver drops it, and for every open request on server start):
//!
//!   1. Search online drivers in Redis GEO around the pickup, nearest first, in widening rings
//!      (3 km, then 6 km after 30 s, then 10 km after 60 s).
//!   2. Keep those with a live heartbeat, then ask Postgres which are eligible: approved, with an
//!      active car, taking this ride type, wheelchair-accessible when the rider needs it, not on a
//!      ride, not deciding another offer, and not already asked about this ride.
//!   3. Offer the ride to the nearest one for [OFFER_TTL_SECS] (pushed over the live socket and
//!      visible in `GET /v1/driver/state`). One driver at a time, so a rider never gets two cars.
//!   4. Accept is a single `UPDATE rides ... WHERE status = 'requested'` (see drivers::accept);
//!      decline/expiry moves on to the next driver.
//!   5. Nobody within [DISPATCH_TIMEOUT_SECS] -> `no_drivers`, and the rider is told.
//!
//! Every step re-reads the ride, so a cancel, an accept, or a restart is always respected. The
//! task holds no state that matters: the `ride_offers` table is the memory.

use std::{collections::HashSet, sync::Arc, time::Duration};

use chrono::{DateTime, Utc};
use dashmap::{mapref::entry::Entry, DashMap};
use serde_json::json;
use sqlx::FromRow;
use tokio::sync::Notify;
use uuid::Uuid;

use crate::{
    account::notify,
    drivers::{self, ONLINE_KEY},
    error::{is_unique_violation, ApiError},
    AppState,
};

/// Time a driver has to answer an offer.
pub const OFFER_TTL_SECS: i64 = 15;
/// A request that nobody accepts in this time ends as `no_drivers`.
pub const DISPATCH_TIMEOUT_SECS: i64 = 120;
/// Search radius by seconds waited: (from, km).
const RINGS: [(i64, f64); 3] = [(0, 3.0), (30, 6.0), (60, 10.0)];
const MAX_CANDIDATES: usize = 50;
/// When nobody is free, look again this often (or sooner, when woken).
const IDLE_RETRY: Duration = Duration::from_secs(3);
/// Scheduled rides start looking for a driver this long before pickup time.
const SCHEDULED_LEAD_MINUTES: i64 = 10;

/// Running dispatch tasks, by ride. Waking one makes it re-check now instead of at its next tick.
#[derive(Clone, Default)]
pub struct Dispatch(Arc<DashMap<Uuid, Arc<Notify>>>);

impl Dispatch {
    pub fn wake(&self, ride: Uuid) {
        if let Some(n) = self.0.get(&ride) {
            n.notify_one();
        }
    }
}

/// Wakes every waiting ride (a driver just came online).
pub fn wake_all(st: &AppState) {
    for entry in st.dispatch.0.iter() {
        entry.value().notify_one();
    }
}

/// Starts dispatching a ride, unless a task already runs for it.
pub fn spawn(st: &AppState, ride: Uuid) {
    let notify = match st.dispatch.0.entry(ride) {
        Entry::Occupied(running) => {
            running.get().notify_one();
            return;
        }
        Entry::Vacant(slot) => slot.insert(Arc::new(Notify::new())).clone(),
    };
    let st = st.clone();
    tokio::spawn(async move {
        if let Err(err) = run(&st, ride, &notify).await {
            tracing::error!(error = ?err, %ride, "dispatch failed");
        }
        st.dispatch.0.remove(&ride);
    });
}

/// On start: pick up open requests (the server may have restarted mid-dispatch), and every 30 s
/// move scheduled rides that are coming due into dispatch.
pub fn spawn_background(st: AppState) {
    tokio::spawn(async move {
        match sqlx::query_scalar::<_, Uuid>("SELECT id FROM rides WHERE status = 'requested'")
            .fetch_all(&st.pool)
            .await
        {
            Ok(open) => {
                if !open.is_empty() {
                    tracing::info!(count = open.len(), "resuming dispatch for open requests");
                }
                for ride in open {
                    spawn(&st, ride);
                }
            }
            Err(err) => tracing::error!(error = ?err, "could not resume dispatch"),
        }
        let mut tick = tokio::time::interval(Duration::from_secs(30));
        loop {
            tick.tick().await;
            promote_scheduled(&st).await;
        }
    });
}

async fn promote_scheduled(st: &AppState) {
    let due: Vec<Uuid> = match sqlx::query_scalar(
        "SELECT id FROM rides WHERE status = 'scheduled' AND scheduled_at <= now() + make_interval(mins => $1)",
    )
    .bind(SCHEDULED_LEAD_MINUTES as i32)
    .fetch_all(&st.pool)
    .await
    {
        Ok(due) => due,
        Err(err) => {
            tracing::error!(error = ?err, "scheduled ride check failed");
            return;
        }
    };
    for ride in due {
        let moved = sqlx::query(
            "UPDATE rides SET status = 'requested', requested_at = now(), updated_at = now()
             WHERE id = $1 AND status = 'scheduled'",
        )
        .bind(ride)
        .execute(&st.pool)
        .await;
        match moved {
            Ok(done) if done.rows_affected() == 1 => {
                tracing::info!(%ride, "scheduled ride due; dispatching");
                tell_riders(st, ride, "requested", None).await;
                spawn(st, ride);
            }
            Ok(_) => {}
            // The rider is on another ride right now; try again next tick.
            Err(err) if is_unique_violation(&err) => tracing::warn!(%ride, "scheduled ride waiting: rider busy"),
            Err(err) => tracing::error!(error = ?err, %ride, "could not promote scheduled ride"),
        }
    }
}

#[derive(FromRow)]
struct Job {
    rider_id: Uuid,
    product_id: String,
    product_name: String,
    dropoff_name: String,
    pickup_lat: f64,
    pickup_lng: f64,
    requested_at: DateTime<Utc>,
    wheelchair: bool,
}

async fn load_job(st: &AppState, ride: Uuid) -> Result<Option<Job>, ApiError> {
    Ok(sqlx::query_as::<_, Job>(
        "SELECT r.rider_id, r.product_id, p.name AS product_name, r.dropoff_name, r.pickup_lat, r.pickup_lng,
                r.requested_at, COALESCE(s.wheelchair_access, false) AS wheelchair
         FROM rides r
         JOIN ride_products p ON p.id = r.product_id
         LEFT JOIN rider_settings s ON s.user_id = r.rider_id
         WHERE r.id = $1 AND r.status = 'requested' AND r.requested_at IS NOT NULL",
    )
    .bind(ride)
    .fetch_optional(&st.pool)
    .await?)
}

fn radius_km(waited_secs: i64) -> f64 {
    RINGS.iter().rev().find(|(from, _)| waited_secs >= *from).map(|(_, km)| *km).unwrap_or(RINGS[0].1)
}

async fn run(st: &AppState, ride: Uuid, notify: &Notify) -> Result<(), ApiError> {
    // Drivers skipped this round only (lock held elsewhere); real "asked" memory is ride_offers.
    let mut skip: HashSet<Uuid> = HashSet::new();
    loop {
        let Some(job) = load_job(st, ride).await? else {
            return Ok(()); // accepted, cancelled or expired
        };
        let waited = (Utc::now() - job.requested_at).num_seconds();
        if waited >= DISPATCH_TIMEOUT_SECS {
            no_drivers(st, ride, &job).await?;
            return Ok(());
        }

        let Some((driver, distance_m)) = next_driver(st, ride, &job, radius_km(waited), &skip).await? else {
            skip.clear();
            tokio::select! {
                _ = notify.notified() => {}
                _ = tokio::time::sleep(IDLE_RETRY) => {}
            }
            continue;
        };
        if !lock_driver(st, driver, ride).await? {
            skip.insert(driver);
            continue;
        }
        let offered = offer(st, ride, driver, distance_m, notify).await;
        unlock_driver(st, driver).await;
        offered?;
    }
}

/// Nearest eligible online driver within `km`, with their distance in metres.
async fn next_driver(st: &AppState, ride: Uuid, job: &Job, km: f64, skip: &HashSet<Uuid>) -> Result<Option<(Uuid, i32)>, ApiError> {
    let mut redis = st.redis.clone();
    let nearby: Vec<(String, f64)> = redis::cmd("GEOSEARCH")
        .arg(ONLINE_KEY)
        .arg("FROMLONLAT").arg(job.pickup_lng).arg(job.pickup_lat)
        .arg("BYRADIUS").arg(km).arg("km")
        .arg("ASC").arg("COUNT").arg(MAX_CANDIDATES)
        .arg("WITHDIST")
        .query_async(&mut redis)
        .await?;
    let nearby: Vec<(Uuid, f64)> = nearby
        .into_iter()
        .filter_map(|(id, d)| Uuid::parse_str(&id).ok().map(|id| (id, d)))
        .filter(|(id, _)| !skip.contains(id))
        .collect();
    if nearby.is_empty() {
        return Ok(None);
    }

    // Drop drivers whose app stopped reporting (crash, no signal) from the search set.
    let mut check = redis::pipe();
    for (id, _) in &nearby {
        check.cmd("EXISTS").arg(drivers::seen_key(*id));
    }
    let alive: Vec<bool> = check.query_async(&mut redis).await?;
    let mut ids = Vec::new();
    let mut stale = Vec::new();
    for ((id, _), ok) in nearby.iter().zip(alive) {
        if ok { ids.push(*id) } else { stale.push(id.to_string()) }
    }
    if !stale.is_empty() {
        let _: () = redis::cmd("ZREM").arg(ONLINE_KEY).arg(&stale).query_async(&mut redis).await?;
    }
    if ids.is_empty() {
        return Ok(None);
    }

    // Eligibility, keeping the distance order from Redis.
    let chosen: Option<Uuid> = sqlx::query_scalar(&format!(
        "SELECT d.user_id
         FROM unnest($1::uuid[]) WITH ORDINALITY AS c(id, ord)
         JOIN drivers d ON d.user_id = c.id AND d.status = 'approved'
         JOIN vehicles v ON v.driver_id = d.user_id AND v.active
         JOIN driver_products dp ON dp.driver_id = d.user_id AND dp.product_id = $2 AND dp.enabled
         WHERE (NOT $3 OR v.wheelchair_accessible)
           AND d.user_id <> $5
           AND NOT EXISTS (SELECT 1 FROM rides r WHERE r.driver_id = d.user_id AND r.status IN ('accepted', 'arriving', 'in_progress'))
           AND NOT EXISTS (SELECT 1 FROM ride_offers o WHERE o.ride_id = $4 AND o.driver_id = d.user_id)
           AND NOT EXISTS (SELECT 1 FROM ride_offers o WHERE o.driver_id = d.user_id AND o.response IS NULL AND o.expires_at > now())
           -- Destination filter: a driver heading home only gets trips that end closer to home.
           AND (d.destination IS NULL OR EXISTS (
                SELECT 1 FROM rides t WHERE t.id = $4
                  AND ST_Distance(ST_SetSRID(ST_MakePoint(t.dropoff_lng, t.dropoff_lat), 4326)::geography, d.destination)
                      < ST_Distance(t.pickup, d.destination)))
         ORDER BY c.ord LIMIT 1"
    ))
    .bind(&ids)
    .bind(&job.product_id)
    .bind(job.wheelchair)
    .bind(ride)
    .bind(job.rider_id)
    .fetch_optional(&st.pool)
    .await?;
    Ok(chosen.and_then(|id| nearby.iter().find(|(n, _)| *n == id).map(|(_, km)| (id, (km * 1000.0).round() as i32))))
}

fn lock_key(driver: Uuid) -> String {
    format!("nova:driver:{driver}:offer")
}

/// One offer per driver at a time, even across several API instances.
async fn lock_driver(st: &AppState, driver: Uuid, ride: Uuid) -> Result<bool, ApiError> {
    let mut redis = st.redis.clone();
    let got: Option<String> = redis::cmd("SET")
        .arg(lock_key(driver)).arg(ride.to_string())
        .arg("NX").arg("EX").arg(OFFER_TTL_SECS + 5)
        .query_async(&mut redis)
        .await?;
    Ok(got.is_some())
}

async fn unlock_driver(st: &AppState, driver: Uuid) {
    let mut redis = st.redis.clone();
    let res: Result<(), _> = redis::cmd("DEL").arg(lock_key(driver)).query_async(&mut redis).await;
    if let Err(err) = res {
        tracing::warn!(error = ?err, %driver, "could not release offer lock");
    }
}

/// Offers the ride to one driver and waits for their answer or the deadline.
async fn offer(st: &AppState, ride: Uuid, driver: Uuid, distance_m: i32, notify: &Notify) -> Result<(), ApiError> {
    let id = Uuid::now_v7();
    let created = sqlx::query(
        "INSERT INTO ride_offers (id, ride_id, driver_id, distance_meters, expires_at)
         VALUES ($1, $2, $3, $4, now() + make_interval(secs => $5))
         ON CONFLICT (ride_id, driver_id) DO NOTHING",
    )
    .bind(id)
    .bind(ride)
    .bind(driver)
    .bind(distance_m)
    .bind(OFFER_TTL_SECS as f64)
    .execute(&st.pool)
    .await?;
    if created.rows_affected() == 0 {
        return Ok(());
    }
    tracing::info!(%ride, %driver, distance_m, "offer sent");
    if let Some(event) = drivers::offer_event(st, driver).await {
        st.hub.send(driver, event);
    }

    let deadline = tokio::time::Instant::now() + Duration::from_secs(OFFER_TTL_SECS as u64);
    loop {
        tokio::select! {
            _ = notify.notified() => {}
            _ = tokio::time::sleep_until(deadline) => {}
        }
        let response: Option<String> = sqlx::query_scalar("SELECT response FROM ride_offers WHERE id = $1")
            .bind(id)
            .fetch_one(&st.pool)
            .await?;
        if response.is_some() {
            return Ok(());
        }
        if tokio::time::Instant::now() >= deadline {
            let expired = sqlx::query(
                "UPDATE ride_offers SET response = 'expired', responded_at = now() WHERE id = $1 AND response IS NULL",
            )
            .bind(id)
            .execute(&st.pool)
            .await?;
            if expired.rows_affected() == 1 {
                st.hub.send(driver, json!({ "type": "offer_closed", "offer_id": id, "reason": "expired" }));
            }
            return Ok(());
        }
        // Woken for another reason (rider cancelled -> the ride re-check in run() ends us).
        if load_job(st, ride).await?.is_none() {
            return Ok(());
        }
    }
}

async fn no_drivers(st: &AppState, ride: Uuid, job: &Job) -> Result<(), ApiError> {
    let ended = sqlx::query(
        "UPDATE rides SET status = 'no_drivers', ended_at = now(), updated_at = now()
         WHERE id = $1 AND status = 'requested'",
    )
    .bind(ride)
    .execute(&st.pool)
    .await?;
    if ended.rows_affected() == 1 {
        tracing::info!(%ride, "no driver found");
        tell_riders(st, ride, "no_drivers", Some(("no_drivers", format!("No {} driver found", job.product_name),
            format!("Your ride to {} wasn't matched. You haven't been charged.", job.dropoff_name)))).await;
    }
    Ok(())
}

/// Tells the rider (and the Nova user it was booked for) about a ride change: a live socket event
/// always, plus an inbox entry when `inbox` is given as (event, title, body).
pub(crate) async fn tell_riders(st: &AppState, ride: Uuid, status: &str, inbox: Option<(&str, String, String)>) {
    let people: Option<(Uuid, Option<Uuid>)> = sqlx::query_as("SELECT rider_id, passenger_user_id FROM rides WHERE id = $1")
        .bind(ride)
        .fetch_optional(&st.pool)
        .await
        .unwrap_or(None);
    let Some((rider, passenger)) = people else { return };
    for user in std::iter::once(rider).chain(passenger) {
        st.hub.send(user, json!({ "type": "ride_update", "ride_id": ride, "status": status }));
        if let Some((event, title, body)) = &inbox {
            notify(st, user, "trip", event, title, body, Some(ride)).await;
        }
    }
}

/// Withdraws open offers for a ride the rider cancelled, and tells its driver if it had one.
pub(crate) async fn rider_cancelled(st: &AppState, ride: Uuid, driver: Option<Uuid>) {
    let withdrawn: Vec<(Uuid, Uuid)> = sqlx::query_as(
        "UPDATE ride_offers SET response = 'withdrawn', responded_at = now()
         WHERE ride_id = $1 AND response IS NULL RETURNING id, driver_id",
    )
    .bind(ride)
    .fetch_all(&st.pool)
    .await
    .unwrap_or_default();
    for (offer, to) in withdrawn {
        st.hub.send(to, json!({ "type": "offer_closed", "offer_id": offer, "reason": "withdrawn" }));
    }
    if let Some(driver) = driver {
        st.hub.send(driver, json!({ "type": "ride_update", "ride_id": ride, "status": "cancelled" }));
        notify(st, driver, "trip", "ride_cancelled", "Rider cancelled", "The trip was cancelled. You're free for new requests.", Some(ride)).await;
    }
    st.dispatch.wake(ride);
}
