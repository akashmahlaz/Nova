# Ride-Hailing Backend: Deep Research Document

> [!IMPORTANT]
> This document covers the **unsolved and under-discussed** backend problems — not the basic dispatch loop. These are the systems that determine whether your app survives real-world usage or collapses under edge cases.

---

## 1. Failure Recovery & In-Memory State Resurrection

The Rust backend holds all driver positions and active dispatch offers **in memory**. What happens when:
- The Rust process crashes or OOM-kills at 3 AM?
- You deploy a new version and restart?
- The VPS host reboots?

**Every active trip and every driver's online status vanishes.**

### Solution: Write-Ahead Event Log + State Reconstruction

The Rust service must maintain a **WAL (Write-Ahead Log)** of critical state mutations to PostgreSQL or Redis Streams:

```
Event Log (PostgreSQL or Redis Stream):
───────────────────────────────────────
| seq | event_type         | payload                              | timestamp           |
|-----|--------------------|--------------------------------------|---------------------|
| 101 | driver_online      | { driver_id, lat, lng, h3_cell }     | 2026-11-14 03:40:01 |
| 102 | driver_location    | { driver_id, lat, lng, heading }     | 2026-11-14 03:40:03 |
| 103 | trip_requested     | { trip_id, rider_id, pickup, dest }  | 2026-11-14 03:40:05 |
| 104 | offer_sent         | { trip_id, driver_id, expires_at }   | 2026-11-14 03:40:05 |
| 105 | offer_accepted     | { trip_id, driver_id }               | 2026-11-14 03:40:12 |
```

**On restart**, the Rust service replays the event log from the last checkpoint:
1. Rebuild `DashMap<DriverId, DriverRecord>` from all `driver_online` / `driver_location` events younger than 30 seconds.
2. Rebuild active trip state machines from `trip_requested` → `offer_sent` → `offer_accepted` chains.
3. For any offer whose 15-second timer was mid-flight at crash time: either re-offer (if within window) or cascade to next driver.

**Without this, a simple restart loses all in-flight rides and all online drivers disappear from the map.**

---

## 2. Idempotency (The Double-Tap Problem)

### The Problem:
Rider's phone is on a flaky 3G network. They tap "Request Ride". The HTTP request times out. The app retries automatically. Now your backend receives **two identical ride requests** 800ms apart.

Without idempotency, you create two trips, dispatch two drivers, charge twice.

### Solution: Client-Generated Idempotency Keys

```
POST /api/v1/trips
Headers:
  Idempotency-Key: "rider_abc_1731541200_req_7f3a"
```

The Rust backend:
1. Before processing, check: `SELECT id FROM trips WHERE idempotency_key = $1`.
2. If a row exists → return the existing trip (no new creation).
3. If no row → insert with the idempotency key → proceed with dispatch.
4. The key includes a timestamp component so stale keys can be garbage-collected after 24 hours.

**This pattern must apply to every mutating endpoint**: ride request, ride accept, ride cancel, payment charge.

---

## 3. Connection Management & Reconnection Protocol

### The Problem:
Mobile networks are unreliable. A driver on a moving vehicle will experience:
- Frequent TCP connection drops (cell tower handoffs every 30-60 seconds at highway speed).
- WebSocket disconnections lasting 2-30 seconds.
- Complete signal loss in tunnels, parking garages, and rural areas (lasting minutes).

### What Must Happen at Each Layer:

| Duration of Disconnect | Driver App Behavior | Rust Backend Behavior |
|---|---|---|
| **< 5 seconds** | Auto-reconnect WebSocket silently. Batch queued GPS pings and send them on reconnect with timestamps. | Accept late pings, update position. No status change. |
| **5–30 seconds** | Show "Reconnecting..." banner. Continue collecting GPS locally in a ring buffer. | If driver is `LockedForOffer`: pause the 15-second timer (don't cascade yet). If driver is `on_trip`: no action, rider sees stale but last-known position. |
| **30–120 seconds** | Show "Connection Lost" warning. Keep buffering GPS. | Evict from spatial index (stop dispatching to them). If mid-offer: cascade to next driver. If `on_trip`: mark trip as `connection_lost` but do NOT cancel. |
| **> 2 minutes** | Prompt driver to check network. | If `on_trip`: alert rider "Driver connection lost, monitoring...". If idle: mark fully offline. |

### The "Mid-Trip Disconnect" Edge Case:
The driver is actively driving a passenger. Their phone loses signal in a tunnel for 3 minutes.
- You **cannot** cancel the trip. The passenger is physically in the car.
- You **cannot** stop metering. The ride is happening.
- The backend must continue the trip state, estimate position along the last-known route heading, and reconcile actual GPS data when the driver reconnects and flushes their buffered coordinates.

---

## 4. Scheduled Rides & Pre-Dispatch

Most backends only handle "I need a ride NOW". But scheduled rides ("Pick me up tomorrow at 6 AM for my flight") are a completely different dispatching problem.

### Why You Can't Just Run Normal Dispatch at 6 AM:
- At 5:55 AM, there might be zero drivers online near the airport area.
- If you wait until exactly 6:00 AM to search, you're already late.

### The Pre-Dispatch Pipeline:
```
T-24h:  Ride scheduled. Stored in `scheduled_trips` table.
T-45m:  Background cron job wakes up. Identifies trips departing in next 60 minutes.
T-30m:  System checks driver supply in the pickup H3 zone.
        If supply is thin → send push notifications to offline drivers in the area:
        "Scheduled ride available at 6:00 AM near Airport Terminal 2. Accept?"
T-15m:  Run normal dispatch cascade against confirmed/online drivers.
T-5m:   If still no driver → escalate: widen search radius, offer surge bonus to drivers.
T-0:    Driver should already be en route or arrived.
```

### The Overbooking Problem:
If a driver accepts a scheduled ride at T-30m, they might also accept a regular on-demand ride at T-20m and now have conflicting trips. The backend must **lock the driver's availability window** for 15 minutes before the scheduled pickup.

---

## 5. Ride Pooling / Shared Rides (The Hard Matching Problem)

Pooling (UberPool / Lyft Shared) is **exponentially more complex** than single-rider dispatch.

### Why It's Different:
- A driver already carrying Passenger A gets a request from Passenger B.
- The backend must compute: Can we pick up B **without adding more than X minutes** to A's trip?
- This requires solving a constrained optimization:

```
Given:
  Driver current position: D
  Passenger A destination: A_dest
  Passenger B pickup: B_pickup
  Passenger B destination: B_dest

Compute all permutations of stops:
  Route 1: D → B_pickup → A_dest → B_dest
  Route 2: D → B_pickup → B_dest → A_dest
  Route 3: D → A_dest → B_pickup → B_dest  (pick up B after dropping A)

Constraints:
  - A's detour must not exceed 5 minutes over their original ETA.
  - B's total trip time must not exceed 1.5x their direct trip time.
  - Vehicle capacity (seats) must not be exceeded.
```

### Recommendation:
**Do NOT build pooling in V1.** It requires a fundamentally different matching algorithm (batch optimization, not greedy sequential dispatch). Ship single-rider first, add pooling as V2 after the core engine is stable.

---

## 6. Safety & Emergency Infrastructure

### SOS / Panic Button:
When a rider or driver presses the emergency button:
1. **Immediately** freeze the trip state (prevent cancellation or completion).
2. Record and persist GPS coordinates at **1-second intervals** (override the normal 5-10s cadence).
3. Trigger an alert to your operations dashboard with: trip ID, both parties' identities, live GPS stream, and a direct phone number.
4. Optionally auto-dial local emergency services (requires country-specific emergency number integration).

### Trip Sharing (Share My Ride):
- Rider sends a link to a friend/family member.
- The link opens a **read-only live map** showing the driver's real-time position along the route.
- This is a separate Supabase Realtime channel subscription with read-only auth: `trip_share:{share_token}`.
- The share token is a signed JWT with an expiry matching the trip's estimated end time + 30 minutes.

### Masked Phone Numbers:
Riders and drivers should **never** see each other's real phone numbers.
- Use a telephony proxy (Twilio Proxy or Exotel for India) that assigns a temporary virtual number for each trip.
- When the driver calls the rider, it routes through the proxy. The virtual number expires when the trip ends.

---

## 7. Communication Between Rider & Driver (In-App Messaging)

### Why This Matters:
After a driver accepts, they often need to coordinate:
- "I'm in the red Toyota, parked across the street."
- "Gate number 3, not the main entrance."
- "I'll be 2 minutes late, please wait."

### Architecture:
- Use a **Supabase Realtime Presence Channel** scoped to the trip: `chat:{trip_id}`.
- Messages are ephemeral (stored only for the trip duration) — you don't need a full chat database.
- Persist the last 5 messages to PostgreSQL for dispute resolution auditing.
- Pre-built quick-reply buttons reduce typing while driving: `"On my way"`, `"I've arrived"`, `"Running late"`.

---

## 8. Rating, Trust Scores & Deactivation Engine

### The Feedback Loop:
After every trip, both rider and driver rate each other (1-5 stars). This seems simple but has complex backend consequences:

### Rolling Average (Not Lifetime Average):
- Uber/Lyft use a **rolling window of the last 500 rated trips**, not a lifetime average.
- A driver who had bad ratings 2 years ago but has been excellent for the last 6 months should not be penalized.

### How Ratings Affect Dispatch Priority:
- Two drivers equidistant from a rider: the one with the higher rolling rating gets the offer first.
- Rating is a weighted factor in the scoring function:
  ```
  Score = (0.5 × normalized_eta) + (0.3 × normalized_rating) + (0.2 × idle_wait_time)
  ```

### Automated Deactivation Thresholds:
| Metric | Warning | Deactivation |
|---|---|---|
| Rolling rating | < 4.5 → warning email | < 4.2 → temporary suspension |
| Cancellation rate (driver-initiated) | > 15% in last 50 trips | > 25% → review |
| Acceptance rate | < 60% → reduced priority | < 40% → shadowban from dispatch |
| Trip completion rate | < 85% → warning | < 70% → deactivation |

### Fraud Patterns the Backend Must Detect:
- **GPS Spoofing:** Driver fakes their location to appear near a surge zone. Detect by checking if consecutive GPS pings imply physically impossible speed (> 200 km/h).
- **Trip Inflation:** Driver takes intentionally longer routes. Compare actual driven distance vs. OSRM optimal route. If actual > 1.4× optimal, flag for review.
- **Phantom Trips:** Driver starts and completes a trip without a real passenger. Cross-reference: did the rider's phone GPS actually move along the trip route?

---

## 9. Service Area Boundaries & Geo-Fencing

### The Problem:
Your service doesn't cover an entire country on day one. You launch in one city.

### What the Backend Must Enforce:
1. **Service Area Polygon:** Stored as a PostGIS `GEOMETRY(POLYGON)`. When a rider sets a pickup location, check:
   ```sql
   SELECT ST_Contains(
     (SELECT boundary FROM service_areas WHERE city = 'mumbai'),
     ST_SetSRID(ST_MakePoint($lng, $lat), 4326)
   );
   ```
   If `false` → return "Service not available in this area".

2. **Cross-Boundary Trips:** Rider is inside your service area, but the destination is outside (e.g., an intercity trip). Policy decision:
   - Allow it but charge a premium?
   - Allow it but the driver won't get return-trip dispatch in the destination city?
   - Block it entirely?

3. **Airport / Mall / Stadium Geo-Fences:** Special zones with custom rules:
   - **Airport pickup queue:** Drivers must wait in a virtual FIFO queue (not nearest-wins). The driver who has been waiting longest in the airport geo-fence gets the next airport pickup.
   - **Stadium surge zones:** After a cricket match ends, 30,000 people request rides simultaneously. Pre-position the surge multiplier to attract drivers 30 minutes before the match ends.

---

## 10. Payment Edge Cases & Failure Modes

### Scenarios That Break Naive Payment Flows:

| Scenario | What Happens |
|---|---|
| **Rider's card declines after trip completes** | Trip already happened. Driver must still be paid. Backend must: retry charge 3 times over 24 hours, then add debt to rider's account, block future rides until paid. |
| **Rider disputes charge with their bank (chargeback)** | Payment provider sends a webhook. Backend must: freeze rider account, submit trip GPS evidence to payment provider for dispute, flag rider for manual review. |
| **Cash ride (common in India/SEA)** | No card charge at all. Driver collects cash. But the platform's 20% commission must be deducted from the driver's future earnings or wallet. Backend must track cash debt. |
| **Promo codes / Coupons** | Applied at booking time. Backend must: validate code hasn't expired, hasn't exceeded usage limit, isn't already used by this rider, apply discount to fare calculation before charging. Promo abuse detection: same rider creating multiple accounts to reuse first-ride promos. |
| **Tips (post-trip)** | Rider can add a tip up to 2 hours after trip completion. This is a separate ledger entry, 100% credited to driver (no platform cut). Must handle: what if rider adds tip, then disputes the entire charge? |
| **Toll roads** | Driver takes a toll road during the trip. Backend must: either detect toll via geo-fence + route matching and auto-add to fare, or let driver submit toll receipt for reimbursement. |

---

## 11. Observability & Operational Health

### What You Must Monitor in Production:

```
┌─────────────────────────────────────────────────────────┐
│                    METRICS DASHBOARD                     │
├─────────────────────────────────────────────────────────┤
│  Dispatch Success Rate     [████████░░]  82%            │
│  Median Pickup ETA         3m 42s                       │
│  Offer Accept Rate         [██████████]  94%            │
│  Ghost Driver Evictions    12/min                       │
│  WebSocket Reconnections   340/min                      │
│  Active Trips              1,247                        │
│  Drivers Online            3,891                        │
│  OSRM Query P99 Latency   8ms                          │
│  DB Connection Pool Usage  [████░░░░░░]  38/100         │
│  Failed Payment Charges    7 (last hour)                │
└─────────────────────────────────────────────────────────┘
```

### Critical Alerts:
- **Dispatch Success Rate drops below 70%:** Either drivers are offline, ghost drivers aren't being evicted, or OSRM is returning bad ETAs.
- **Median pickup ETA exceeds 10 minutes:** Supply shortage. Consider triggering automated surge pricing to attract more drivers.
- **Payment failure rate exceeds 5%:** Payment gateway issue. Switch to backup gateway or pause new bookings.

---

## 12. Multi-Tenancy & White-Label Architecture

Since you mentioned rebranding for clients — if you plan to sell this as a white-label platform, the backend must support multi-tenancy from day one:

### Tenant Isolation Strategy:
- **Shared database, tenant column:** Every table has a `tenant_id` column. All queries include `WHERE tenant_id = $1`. Simpler but riskier (one bad query leaks data).
- **Schema-per-tenant:** Each client gets their own PostgreSQL schema (`client_mumbai.trips`, `client_delhi.trips`). Better isolation, slightly more operational overhead.
- **Database-per-tenant:** Maximum isolation but highest cost. Only needed for regulatory/compliance reasons.

### What Changes Per Tenant:
- Fare structure (base fare, per-km rate, surge rules).
- Service area polygons.
- Vehicle categories (Bike, Auto, Mini, Sedan, SUV, Premium).
- Payment gateway credentials.
- Push notification sender IDs (each client has their own Firebase project).
- Branding assets (logo, colors — but this is frontend, not backend).

---

## 13. Vehicle Categories & Fare Structures

### The Problem Most Backends Miss:
A ride-hailing app doesn't have one fare. It has N fare structures, one per vehicle category, per city, per time-of-day:

```
fare_rules table:
┌───────────┬──────────┬───────────┬──────────┬──────────┬───────────┬────────────┐
│ city      │ category │ base_fare │ per_km   │ per_min  │ min_fare  │ surge_cap  │
├───────────┼──────────┼───────────┼──────────┼──────────┼───────────┼────────────┤
│ mumbai    │ auto     │ ₹25       │ ₹12      │ ₹1.5     │ ₹35       │ 2.0x       │
│ mumbai    │ mini     │ ₹40       │ ₹14      │ ₹2.0     │ ₹60       │ 2.5x       │
│ mumbai    │ sedan    │ ₹60       │ ₹18      │ ₹2.5     │ ₹80       │ 3.0x       │
│ mumbai    │ suv      │ ₹80       │ ₹22      │ ₹3.0     │ ₹120      │ 2.5x       │
│ delhi     │ bike     │ ₹15       │ ₹8       │ ₹1.0     │ ₹25       │ 2.0x       │
└───────────┴──────────┴───────────┴──────────┴──────────┴───────────┴────────────┘
```

### Night Surge / Time-Based Pricing:
- 11 PM – 5 AM: Apply a 1.2x night charge multiplier regardless of demand-based surge.
- Peak hours (8–10 AM, 5–8 PM): Lower the surge activation threshold.
- These rules are config-driven, not hardcoded.

### Fare Dispute Resolution:
- Rider says "I was charged ₹450 but the app showed ₹320."
- Backend must store: the **upfront estimate** at booking time AND the **actual metered fare** at trip end.
- If the difference exceeds 20%, auto-flag for review and show the rider a breakdown:
  ```
  Upfront estimate: ₹320 (12.4 km, 28 min estimated)
  Actual fare: ₹450 (12.4 km, 52 min actual — heavy traffic)
  Reason: Extended trip duration due to traffic (24 extra minutes × ₹2.5/min + surge 1.3x)
  ```

---

## Summary: What We've Now Covered vs. What's Left

| Area | Status |
|---|---|
| Dispatch + H3 + State Machine | ✅ Previously covered |
| Ghost driver eviction | ✅ Previously covered |
| OSRM routing integration | ✅ Previously covered |
| 4-digit PIN verification | ✅ Previously covered |
| Double-entry financial ledger | ✅ Previously covered |
| **Crash recovery & state resurrection** | ✅ **New — covered above** |
| **Idempotency (double-tap prevention)** | ✅ **New — covered above** |
| **Connection loss & reconnection protocol** | ✅ **New — covered above** |
| **Scheduled rides & pre-dispatch** | ✅ **New — covered above** |
| **Ride pooling complexity** | ✅ **New — covered above** |
| **Safety: SOS, trip sharing, masked numbers** | ✅ **New — covered above** |
| **In-app messaging** | ✅ **New — covered above** |
| **Rating engine & fraud detection** | ✅ **New — covered above** |
| **Geo-fencing & service boundaries** | ✅ **New — covered above** |
| **Payment edge cases (cash, disputes, tolls)** | ✅ **New — covered above** |
| **Observability & monitoring** | ✅ **New — covered above** |
| **Multi-tenancy / white-label** | ✅ **New — covered above** |
| **Vehicle categories & fare rules** | ✅ **New — covered above** |
