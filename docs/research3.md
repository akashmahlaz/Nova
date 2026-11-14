There are **4 remaining gaps** worth acknowledging, but none of them block us from starting the core engine. Let me list them quickly, then give you the build order.

---

### Remaining Gaps (Build Later, Not Blockers)

| Area | Why It's Not a Blocker |
|---|---|
| **Driver Onboarding (KYC, license upload, vehicle docs)** | It's a CRUD workflow with a document approval state machine. Standard stuff you can bolt on after the dispatch engine works. |
| **Push Notification Delivery (FCM/APNs)** | Critical for production, but during development you test with the app open. Add FCM integration after the WebSocket dispatch loop is proven. |
| **API Versioning** | Matters when you have live users with old app versions. Not relevant until you ship V1. |
| **Load Testing & Simulation** | You need a simulator that spawns 500 fake drivers moving along real roads to stress-test dispatch. Build it after the engine exists. |

None of these change the core data model or dispatch architecture. They layer on top.

---

### The Research Is Solid. Here's the Build Order.

```
Phase 1: Foundation (Week 1)
├── Rust project scaffold (Cargo + Axum + Tokio + SQLx)
├── Supabase project setup (PostGIS extension, Auth)
├── PostgreSQL schema & migrations
│   ├── profiles, drivers, vehicles
│   ├── trips (with full state enum + idempotency_key)
│   ├── fare_rules (per city, per vehicle category)
│   ├── ledger_entries (double-entry)
│   └── service_areas (PostGIS polygons)
└── Core data types & enums in Rust

Phase 2: Dispatch Engine (Week 2)
├── In-memory fleet index (DashMap + h3o)
├── Driver heartbeat ingestion (WebSocket endpoint)
├── Ghost driver TTL eviction loop
├── H3 k-ring candidate search
├── OSRM Docker setup + Rust HTTP client
├── Candidate scoring & ranking
└── 15-second cascading offer loop (Tokio select!)

Phase 3: Trip Lifecycle (Week 3)
├── Full state machine (Searching → Accepted → Arrived → InProgress → Completed)
├── Atomic DB locking (prevent double-accept)
├── 4-digit PIN generation & verification
├── Cancellation matrix (who pays, re-dispatch logic)
├── Fare calculation engine (base + distance + time + surge)
└── Event log for crash recovery

Phase 4: Integration (Week 4)
├── Supabase Realtime channel hookup (live GPS broadcast)
├── In-app chat (ephemeral trip channel)
├── Payment flow (Stripe/Razorpay charge + ledger entries)
├── Rating submission & rolling average
└── Connect mobile apps to backend APIs
```

**Phase 1 is pure database and type definitions** — the foundation everything else depends on. Shall we start writing the PostgreSQL schema and Rust project scaffold?