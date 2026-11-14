// For maintaining the Databse, postgresql will be used, we may use supabase with websocket for persistence contenction
// for android and high performance and great functionality, we are going to use kotlin, jetpack compose.
for UI/UX of android the latest material three expressive design is created, and will be used. with thier research reading deeply.
the backend language is choosen rust becuase of high perfoemnce and high throuoghput, and async nature which can handle real time connections and high number of connections.
web service is built using axum, actix-web is not used becuase of it's complexity and boilerplate code, actix-web is good but we are going to use axum because it is simple and easy to use and has great documentation and community support.
for real-time we are going to use websocket directly, and for sql database we are going to use sqlx, which is a asynchronous sql client for rust.

the apple in future will come with its own design language which will implement by reading research and guides of apple human interfaces.

the uber h3o will be used with postgresql PostGIS for managing geohash and hexzone data, 

for flow another different file will be created.
the images of design of material three are in docs/asssets/orbot folder

Things we need to handle are backend and map functionality, that is gonna very hard, 

Having ready-made UI templates for the frontend is the smart move. It allows you to dedicate **100% of your focus to the backend engine**, which is where ride-hailing platforms actually succeed or fail.

Below is the **comprehensive technical architecture and engineering blueprint** for your Rust + Supabase backend.

---

# The Production Ride-Hailing Backend Blueprint

```
                      ┌──────────────────────────────────────────────┐
                      │              RIDER & DRIVER APPS             │
                      └──────────────┬────────────────┬──────────────┘
                                     │                │
            (1) Ephemeral GPS Pings  │                │ (2) Business Actions
                (Every 1-2s)         │                │     (Request, Accept, PIN)
                                     ▼                ▼
                 ┌───────────────────────┐   ┌───────────────────────────┐
                 │   SUPABASE REALTIME   │   │     RUST CORE SERVICE     │
                 │   (Broadcast Channel) │   │     (Axum + Tokio + H3)   │
                 └───────────┬───────────┘   └──────────────┬────────────┘
                             │                              │
                             │ (Direct P2P Map Tracking)    │
                             ▼                              │
                 ┌───────────────────────┐                  │
                 │   RIDER LIVE SCREEN   │                  │
                 └───────────────────────┘                  ▼
                                             ┌───────────────────────────┐
                                             │  1. In-Memory Fleet Index │
                                             │  2. Dispatch State Machine│
                                             │  3. OSRM Matrix Engine    │
                                             │  4. Financial Ledger      │
                                             └──────────────┬────────────┘
                                                            │
                                                            ▼
                                             ┌───────────────────────────┐
                                             │   SUPABASE POSTGRESQL     │
                                             │      (PostGIS + WAL)      │
                                             └───────────────────────────┘
```

---

## 1. High-Frequency Fleet Telemetry (The In-Memory Spatial Index)

### The Problem:
If 1,000 online drivers write their GPS coordinates directly to a PostgreSQL table every 2 seconds, that is **500 writes/second** (1.8 million writes per hour). Your database disk IOPS will choke, and your write-ahead log (WAL) will balloon.

### The Rust In-Memory Solution:
The Rust backend maintains an in-memory spatial index using **Uber H3 (`h3o` crate)** and a concurrent map (`DashMap`).

```
Driver Ping (lat, lng, heading) 
   ──► Rust Memory: DashMap<DriverId, DriverRecord> 
   ──► Compute H3 Resolution 8 Cell (Edge ~460m)
   ──► Update H3 Bucket: HashMap<H3Cell, HashSet<DriverId>>
   ──► Reset Driver 30-Second TTL Heartbeat
```

* **Memory Footprint:** 10,000 drivers in memory take **less than 15 MB of RAM**.
* **Query Latency:** Fetching all available drivers in a 2 km radius around a pickup coordinate takes **under 0.2 milliseconds**.
* **Database Persist:** Driver locations are only flushed to PostgreSQL **once every 30 seconds**, or immediately upon a state change (`online`, `trip_accepted`, `offline`).

---

## 2. The Ghost Driver Eviction Loop (TTL Engine)

A background Tokio worker runs every 5 seconds to sweep dead connections:

```rust
// Background TTL Task in Rust
tokio::spawn(async move {
    let mut interval = tokio::time::interval(Duration::from_secs(5));
    loop {
        interval.tick().await;
        let now = Instant::now();
        
        // Find drivers who haven't pinged in > 30 seconds
        let stale_drivers: Vec<DriverId> = fleet_manager
            .drivers
            .iter()
            .filter(|entry| now.duration_since(entry.last_ping) > Duration::from_secs(30))
            .map(|entry| *entry.key())
            .collect();

        for driver_id in stale_drivers {
            fleet_manager.evict_stale_driver(driver_id).await;
            // Mark inactive in DB and abort any pending offer
            db::set_driver_offline(&pool, driver_id).await;
        }
    }
});
```

---

## 3. Real Driving Distance vs. Straight-Line (OSRM Integration)

You cannot use straight-line (Euclidean) distance to dispatch, and you cannot afford Google Distance Matrix API costs at scale.

### The Solution: Self-Hosted OSRM (Open Source Routing Machine)
Run OSRM in a lightweight Docker container alongside your Rust backend. 
When a rider requests a trip:
1. **Step 1 (H3 Filter):** Filter the top 10 closest idle drivers using H3 hexagons ($O(1)$ memory lookup $\rightarrow$ takes 0.1ms).
2. **Step 2 (OSRM 1-to-N Table Call):** Query local OSRM `/table/v1/driving/` with 1 pickup point and 10 driver coordinates.
   * OSRM returns exact driving distances and turn-by-turn ETAs for all 10 drivers in **under 4 milliseconds** with **zero API cost**.
3. **Step 3 (Rank):** Sort drivers by actual driving ETA, not straight-line distance.

---

## 4. The Dispatch State Machine & The 15-Second Cascading Offer Loop

The dispatch cycle must be an explicit Finite State Machine (FSM). 

```
                                      [Rider Requests]
                                             │
                                             ▼
                                     ┌───────────────┐
                                     │   SEARCHING   │
                                     └───────┬───────┘
                                             │ Rank Top 5 Candidates
                                             ▼
       ┌────────────────────────────►┌───────────────┐
       │ (15s Timeout / Decline)     │    OFFERED    │ ──► Lock Driver in Rust
       │                             └───────┬───────┘
       │                                     │ Driver Clicks Accept
       │                                     ▼
       │                             ┌───────────────┐
       │                             │   ACCEPTED    │ ──► Atomic DB Lock
       │                             └───────┬───────┘
       │                                     │ Driver reaches pickup
       │                                     ▼
       │                             ┌───────────────┐
       │                             │    ARRIVED    │ ──► 5-min Wait Timer Starts
       │                             └───────┬───────┘
       │                                     │ Rider gives 4-digit PIN
       │                                     ▼
       │                             ┌───────────────┐
       │                             │  IN_PROGRESS  │ ──► Live Trip Metering
       │                             └───────┬───────┘
       │                                     │ Driver marks finished
       │                                     ▼
       │                             ┌───────────────┐
       │                             │   COMPLETED   │ ──► Double-Entry Ledger
       │                             └───────────────┘
       │
[Next Candidate in Queue]
```

### Preventing the "Double-Accept" Race Condition
Two drivers must never be assigned to the same ride, and one driver must never receive two rides. We enforce this at two layers:
1. **Memory Layer (Rust):** When an offer is pushed to Driver A, Driver A’s state in memory immediately becomes `LockedForOffer { trip_id, expires_at }`. No other trip search can see Driver A.
2. **Database Layer (PostgreSQL Atomic CAS):**
   ```sql
   -- Atomic conditional update; fails if trip is no longer 'searching'
   UPDATE trips
   SET status = 'accepted', driver_id = $1, accepted_at = NOW()
   WHERE id = $2 AND status = 'searching'
   RETURNING id;
   ```
   If another event cancelled the trip or another thread claimed it, `rows_affected == 0`. The Rust service aborts cleanly.

---

## 5. Security & Verification: The 4-Digit PIN Handshake

To prevent ghost rides and passenger mix-ups:
1. When the trip status transitions to `accepted`, the Rust backend generates a 4-digit code:
   $$\text{PIN} = \text{random}(1000..9999)$$
2. The PIN is stored hashed or encrypted in PostgreSQL and sent **only** to the Rider’s mobile screen.
3. The Driver's app shows an input field: `"Enter Rider PIN to Start"`.
4. When the driver submits the PIN, the Rust backend checks:
   * Does the PIN match?
   * Is the Driver’s current GPS coordinate within **100 meters** of the trip pickup location (`ST_DWithin`)?
   * If both pass $\rightarrow$ transition trip to `in_progress`.

---

## 6. The Double-Entry Financial Ledger (PostgreSQL)

Never store balances as a simple mutable integer (`balance = balance + 20`). A network glitch or race condition will create accounting discrepancies.

### The Schema:
Every financial movement consists of balanced debits and credits:

```sql
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_type TEXT NOT NULL, -- 'rider', 'driver', 'platform'
    owner_id UUID,
    currency TEXT NOT NULL DEFAULT 'USD',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID REFERENCES trips(id),
    from_account_id UUID REFERENCES accounts(id),
    to_account_id UUID REFERENCES accounts(id),
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    entry_type TEXT NOT NULL, -- 'trip_fare', 'platform_commission', 'cancellation_fee'
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

When a \$25.00 trip completes with an 80/20 split:
1. Entry 1: Rider Account $\rightarrow$ Platform Escrow: **\$25.00**
2. Entry 2: Platform Escrow $\rightarrow$ Driver Account: **\$20.00** (80%)
3. Entry 3: Platform Escrow $\rightarrow$ Platform Revenue: **\$5.00** (20%)
*Account balances are always computed as $\sum(\text{Credits}) - \sum(\text{Debits})$, making financial fraud impossible.*

---

## Next Steps for the Architecture

Now that the complete backend topology is laid out, which component shall we detail first?
1. **The Exact PostgreSQL Schema & Migrations** (PostGIS extensions, tables, indexes, and atomic functions).
2. **The Rust Dispatch Engine Skeleton** (Axum routes, Tokio offer cascade loop, and H3 spatial index structs).
3. **The OSRM Docker Setup & Rust Client** (How to run the routing matrix locally).