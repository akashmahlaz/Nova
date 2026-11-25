FROM rust:1-bookworm AS builder
RUN apt-get update && apt-get install -y libssl-dev pkg-config && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY . .
RUN cargo build --release --locked --bin server
RUN mkdir -p /app/bin && cp target/release/server /app/bin/app

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y libssl3 ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/bin/app /app/bin/app
ENTRYPOINT ["/app/bin/app"]
