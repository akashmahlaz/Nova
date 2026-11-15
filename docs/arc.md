
1. Backend Tech Stack
    - Rust
    - Supabase (PostgreSQL) or Neon alternative have lowest latency,supabase realtime(websockets)
    - PostGIS
    - valhalla (in future)
     - h3o (Uber’s H3 spatial index)
    - Redis
    - axum for WebSockets/REST, (websockets for live GPS streaming)
   





Why was the alone supabase is not enough
1. in ride hailing apps, we need timers, notifications, events , for example offer to ride Driver A and start 15 sec timer, if he ignore or decline-, then immediately ping driver B,  if all driver ping notify rider, no driver available, doing this with supabase was very expensive in term of scale, the more requests come it cpu will exhaust power
2. push notifiactions, 
supabase only works when driver has the app open on thier screen, 
they will not receive Supabase WebSockets.
Express can run the official firebase-admin SDK with persistent connection pools to send high-priority push notifications that sound an audible alert and wake up the driver’s device.

3. Fare Calculation & Third-Party APIs (Routing / Payments)
When a rider types a destination, you need to call a routing API (Mapbox, OSRM, or Google Directions) to get the exact road distance, duration, and calculate the dynamic price.
Having Express handle this keeps your API keys secure and gives you full control over surge pricing algorithms and payment gateway webhooks

we do not route high-frequency live GPS updates through Express.

Live GPS Stream: Driver App 
→
→ Supabase Realtime Broadcast 
→
→ Rider App.
This keeps your Express server lightweight, fast, and cheap because it is not burdened with handling thousands of GPS packets per second.
RC BE- Zero Garbage Collection (GC) pauses: When hundreds of drivers stream locations and matching runs continuously, Node's GC can introduce random 50–200ms latency spikes. Rust provides predictable sub-millisecond execution.
Fearless Concurrency with Tokio: Managing 15-second cascading offer timers, async channels (tokio::sync::mpsc), and state machines in Rust guarantees at compile time that you won't suffer race conditions.

Crates ecosystem: You have h3o (ultra-fast pure Rust implementation of Uber’s H3 spatial index), axum for WebSockets/REST, and sqlx for compile-time verified PostgreSQL queries.

2. Current priority 1. the map dispatch engine
Priority #1: The Map Dispatch Engine (Rust Architecture)

The dispatch engine is the "brain" of your business. It must answer one question: Given a ride request at coordinate 
(
𝑋
,
𝑌
)
(X,Y), which driver should get it, and how do we ensure it is claimed cleanly?
[Rider Request: (lat, lng)]
         │
         ▼
 ┌─────────────────────────────────────────────────────────┐
 │               RUST DISPATCH ENGINE                      │
 │                                                         │
 │ 1. Geohash Lookup: (lat, lng) ──► H3 Cell Index (Res 8) │
 │ 2. k-Ring Expansion: Fetch all cells in radius 1..k     │
 │ 3. Candidate Filter: In-memory `DashMap<DriverId, ...>` │
 │    (Status: Idle, Cell ∈ k-Ring, Heading compatible)    │
 │ 4. Scorer: Rank by ETA / Distance                       │
 └─────────────────────────┬───────────────────────────────┘
                           │
                           ▼
          [Driver 1: 15s Tokio Offer Timer]
            ├── Accepted ──► [Lock Trip & Transition State]
            └── Timeout  ──► [Cascade to Driver 2]



      Instead of hitting PostGIS for every heartbeat, the Rust service keeps an in-memory spatial index of online drivers using Uber H3:


 The Scorer / Ranking Function

Don't just pick the absolute closest driver in a straight line:
Score
=
𝑤
1
⋅
(
Route ETA
)
+
𝑤
2
⋅
(
Driver Rating
)
+
𝑤
3
⋅
(
Idle Wait Time
)
Score=w
1
	​

⋅(Route ETA)+w
2
	​

⋅(Driver Rating)+w
3
	​

⋅(Idle Wait Time)


The Async Cascading Offer Loop (Tokio Task)

Atomic State Machine & Lock (Zero Double-Booking)

To prevent two drivers from claiming the same ride or a driver being assigned two trips at once:

Maintain strict trip states:
Draft
⟶
Searching
⟶
Offered
⟶
Accepted
⟶
Arrived
⟶
InProgress
⟶
Completed
Draft⟶Searching⟶Offered⟶Accepted⟶Arrived⟶InProgress⟶Complet

Supabase Realtime Hookup
The Rust backend can subscribe to or publish directly to Supabase channels using the Supabase Realtime WebSocket protocol, or expose lightweight WebSocket endpoints directly via Axum.
Once Driver 1 accepts, notify the Rider’s private channel (trip:{id}) with the driver's profile, vehicle details, and initial ETA.


edge cases

rivers don't always press "Go Offline". Their battery dies, their phone overheats, they drive into an underground parking lot with zero signal, or Android kills the app in the background. If your backend still thinks they are Online, your dispatch engine will waste 15 seconds offering rides to a dead phone while the rider waits and gets frustrated.

The Backend Solution:

The backend must maintain a TTL (Time-To-Live) Heartbeat Cache (in-memory in Rust using tokio::time).
If a driver does not stream a location ping within 30 seconds, the backend automatically:
Evicts them from the H3 spatial index.
Updates their status in Postgres to inactive_offline.
Cancels any active offer currently waiting on them and immediately cascades to the next driver.



The Routing & ETA Illusion (Why Straight-Line Distance Fails)

The Problem: A driver might be only 300 meters away from the rider in a straight line (Euclidean/Haversine). But:

There is a river, railway track, or median barrier between them.
The driver is on an elevated one-way highway.
The actual driving route requires a 4 km U-turn taking 18 minutes!

If your dispatch ranks drivers by Euclidean distance, you will dispatch drivers who take 20 minutes to arrive while a driver 1 km away could have reached in 3 minutes.

The Cost Problem with Google Maps API:

If 50 riders request rides every minute, and you query Google Distance Matrix for the top 5 drivers each time:
50
×
5
×
60
 min
×
24
 hr
=
360
,
000
 API calls/day
≈
$
1
,
800
/
day
50×5×60 min×24 hr=360,000 API calls/day≈$1,800/day
The Backend Solution: You cannot use Google Distance Matrix for raw dispatch candidate filtering. You need a self-hosted routing engine like OSRM (Open Source Routing Machine) or Valhalla running locally on a cheap $10–$20 VPS, or pre-compute road-network distance matrices directly in Rust.

Driver pulls up to a crowded bar or airport, a random person opens the door and says "Are you my Uber?", driver says "Yes", starts the ride, and drives off with the wrong passenger.
Or, corrupt drivers hit "Start Ride" without picking up the passenger to milk per-kilometer fares.

The Backend Solution:

When a ride is accepted, the backend generates a cryptographically random 4-digit PIN sent only to the Rider's app.
The Driver’s app cannot transition trip state from Arrived 
→
→ InProgress without the driver typing this 4-digit PIN into their phone.
The Rust backend validates the PIN and verifies that the driver's current GPS is within 100 meters of the pickup coordinate before allowing the trip to start.

Dynamic Pricing / Surge & Fare Metering

How does the backend calculate fares accurately?

Upfront Estimate vs. Actual Metered Fare:
Upfront quote: Calculated when searching: 
Base
+
(
Est. Km
×
Rate
)
+
(
Est. Mins
×
Rate
)
×
Surge
Base+(Est. Km×Rate)+(Est. Mins×Rate)×Surge.
Route deviation / Traffic delay: If the ride takes 30 minutes longer due to a major traffic jam or the rider asks for a detour, does the price change?
You need an in-trip metering accumulator in Rust that records actual distance traversed along the road network.
Surge Pricing Calculation:
Calculated per H3 hexagon cell:
Demand Ratio
=
Unfulfilled Ride Requests in Cell
Available Drivers in Cell
Demand Ratio=
Available Drivers in Cell
Unfulfilled Ride Requests in Cell
	​

If 
Ratio
>
1.5
Ratio>1.5, trigger surge multipliers (
1.2
×
,
1.5
×
,
2.0
×
1.2×,1.5×,2.0×) to balance supply.




Dual Ledger / Financial Architecture (Double-Entry Bookkeeping)

You cannot just store a single balance number on a user or driver table. If a network packet drops or a refund happens, numbers get corrupted.

Industry Standard: Double-Entry Ledger System in PostgreSQL:
Every transaction must have two balanced rows: a debit and a credit.
When a trip completes ($20 fare):
Debit Rider Card/Wallet: -$20.00
Credit Platform Fee (20%): +$4.00
Credit Driver Wallet (80%): +$16.00
This guarantees you can audit every cent and prevents financial race conditions.