mod account;
mod admin;
mod contacts;
mod auth;
mod chat;
mod config;
mod dispatch;
mod drivers;
mod error;
mod fare;
mod geo;
mod hub;
mod media;
mod profile;
mod rides;
mod s3;

use std::{sync::Arc, time::Duration};

use axum::{
    extract::State,
    http::{header, HeaderValue, Method, StatusCode},
    routing::get,
    Router,
};
use redis::aio::ConnectionManager;
use sqlx::postgres::PgPoolOptions;
use tower_http::{
    cors::{AllowOrigin, CorsLayer},
    limit::RequestBodyLimitLayer,
    compression::CompressionLayer,
    request_id::{MakeRequestUuid, PropagateRequestIdLayer, SetRequestIdLayer},
    timeout::TimeoutLayer,
    trace::TraceLayer,
};

use crate::config::Config;

#[derive(Clone)]
pub struct AppState {
    pub pool: sqlx::PgPool,
    /// OTP cooldowns, hourly send caps and email code storage. Cloning is cheap (shared socket).
    pub redis: ConnectionManager,
    pub cfg: Arc<Config>,
    pub http: reqwest::Client,
    /// In-memory "H3 cell -> service area" memo (see geo::AreaCache).
    pub areas: geo::AreaCache,
    /// Open WebSockets, for pushing offers and ride updates (see hub.rs).
    pub hub: hub::Hub,
    /// Running dispatch tasks, so a driver's answer wakes the right one (see dispatch.rs).
    pub dispatch: dispatch::Dispatch,
}

#[tokio::main]
async fn main() {
    match dotenvy::dotenv() {
        Ok(path) => println!("Loaded env from: {:?}", path),
        Err(err) => eprintln!("dotenvy error: {:?}", err),
    }
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "server=info,tower_http=info".into()),
        )
        .init();

    let cfg = match Config::from_env() {
        Ok(cfg) => cfg,
        Err(err) => {
            eprintln!("configuration error: {err}");
            std::process::exit(1);
        }
    };
    if cfg.otp_dev_log {
        tracing::warn!("OTP_DEV_LOG=true: OTP codes are logged, not sent. Development only.");
    }

    let pool = PgPoolOptions::new()
        .max_connections(10)
        .connect(&cfg.database_url)
        .await
        .expect("failed to connect to Postgres");
    sqlx::migrate!("./migrations")
        .run(&pool)
        .await
        .expect("failed to run migrations");

    let redis_client = redis::Client::open(cfg.redis_url.as_str()).expect("invalid REDIS_URL");
    let redis = ConnectionManager::new(redis_client)
        .await
        .expect("failed to connect to Redis");

    let bind_addr = cfg.bind_addr.clone();
    // Removes accounts whose 30-day deletion grace period is over (see account.rs).
    account::spawn_deletion_purge(pool.clone());

    let state = AppState {
        pool,
        redis,
        cfg: Arc::new(cfg),
        // One pooled client for Twilio, Resend and Google. Per-call timeouts are set where it's used;
        // this is the outer bound so nothing can hang a worker.
        http: reqwest::Client::builder()
            .timeout(Duration::from_secs(10))
            .build()
            .expect("failed to build HTTP client"),
        areas: geo::AreaCache::default(),
        hub: hub::Hub::default(),
        dispatch: dispatch::Dispatch::default(),
    };
    if state.cfg.driver_auto_approve {
        tracing::warn!("DRIVER_AUTO_APPROVE=true: driver applications are approved without review. Development only.");
    }
    // Resumes open requests after a restart and moves due scheduled rides into dispatch.
    dispatch::spawn_background(state.clone());
    geo::pricing::spawn_surge_job(state.clone());

    let app = Router::new()
        .route("/", get(|| async {
            axum::response::Html(r#"
<!DOCTYPE html>
<html>
<head>
    <title>Nova API</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body { font-family: system-ui, sans-serif; max-width: 600px; margin: 3rem auto; padding: 0 1rem; text-align: center; }
        h1 { color: #2563eb; }
        .status { color: #16a34a; font-weight: bold; }
        .endpoint { background: #f3f4f6; padding: 0.5rem; margin: 0.5rem 0; border-radius: 4px; font-family: monospace; }
        a { color: #2563eb; }
    </style>
</head>
<body>
    <h1>Nova API</h1>
    <p class="status">✓ Service is running</p>
    <p>Backend API for Nova Rider</p>
    <div class="endpoint"><a href="/health">/health</a> - Health check</div>
    <div class="endpoint"><a href="/v1/auth/otp/send">/v1/auth/otp/send</a> - Send OTP</div>
    <div class="endpoint"><a href="/v1/auth/otp/verify">/v1/auth/otp/verify</a> - Verify OTP</div>
    <div class="endpoint"><a href="/v1/me">/v1/me</a> - Current user</div>
</body>
</html>
"#)
        }))
        .route("/health", get(|| async { "Dispatch engine is running! 🏃" }))
        .route(
            "/.well-known/assetlinks.json",
            get(|State(st): State<AppState>| async move {
                let fingerprints: Vec<&str> = st
                    .cfg
                    .passkey_assetlinks_fingerprint
                    .split(',')
                    .map(str::trim)
                    .filter(|s| !s.is_empty())
                    .collect();
                let target = |pkg: &str| {
                    serde_json::json!({
                        "relation": [
                            "delegate_permission/common.handle_all_urls",
                            "delegate_permission/common.get_login_creds"
                        ],
                        "target": {
                            "namespace": "android_app",
                            "package_name": pkg,
                            "sha256_cert_fingerprints": fingerprints
                        }
                    })
                };
                let body = serde_json::json!([
                    target("com.nova.kiwiriders"),
                    target("com.nova.rider"),
                ]);
                (
                    [(axum::http::header::CONTENT_TYPE, "application/json")],
                    axum::Json(body),
                )
            }),
        )
        .merge(auth::router())
        .merge(profile::router())
        .merge(geo::router())
        .merge(rides::router())
        .merge(account::router())
        .merge(contacts::router())
        .merge(drivers::router())
        .merge(hub::router())
        .merge(chat::router())
        .merge(media::router())
        .merge(admin::router())
        .layer(PropagateRequestIdLayer::x_request_id())
        .layer(SetRequestIdLayer::x_request_id(MakeRequestUuid))
        .layer(TraceLayer::new_for_http())
        // Requests that stall longer than 15 seconds get a 408 instead of holding a connection.
        .layer(TimeoutLayer::with_status_code(StatusCode::REQUEST_TIMEOUT, Duration::from_secs(15)))
        // Auth payloads are tiny; anything bigger is rejected before it is read.
        .layer(RequestBodyLimitLayer::new(64 * 1024))
        // gzip JSON responses (search results, route polylines) for riders on mobile data.
        .layer(CompressionLayer::new())
        .layer(
            CorsLayer::new()
                .allow_origin(AllowOrigin::list([
                    HeaderValue::from_static("https://nova-web-production.punjab.fireclaw.in"),
                    HeaderValue::from_static("http://localhost:3000"),
                    HeaderValue::from_static("http://127.0.0.1:3000"),
                    HeaderValue::from_static("http://localhost:3001"),
                    HeaderValue::from_static("http://127.0.0.1:3001"),
                    HeaderValue::from_static("http://localhost:3002"),
                    HeaderValue::from_static("http://127.0.0.1:3002"),
                ]))
                .allow_methods([Method::GET, Method::POST, Method::PATCH, Method::DELETE, Method::OPTIONS])
                .allow_headers([header::AUTHORIZATION, header::CONTENT_TYPE, header::ACCEPT]),
        )
        .with_state(state);

    let listener = tokio::net::TcpListener::bind(&bind_addr)
        .await
        .expect("failed to bind listener");
    tracing::info!("nova api listening on http://{bind_addr}");
    axum::serve(listener, app).await.expect("server error");
}
