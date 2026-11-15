
use axum::{
    routing::get,
    Router,
};

#[tokio::main]
async fn main() {
    let app = Router::new()
        .route("/health", get(health_check));

    let listener = tokio::net::TcpListener::bind("localhost:3000")
        .await
        .unwrap();

    println!("dispatch engine running at http://localhost:3000");
    println!("dispatch engine health http://localhost:3000/health");

    axum::serve(listener, app)
        .await
        .unwrap();
}

async fn health_check() -> &'static str {
    "Dispatch engine is running!"
}
