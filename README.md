# Redrive

Webhook delivery service built with Java 21, Spring Boot 3, PostgreSQL, and Apache Kafka.

Accepts events via REST API and delivers them to subscriber endpoints with retries, backoff, dead-lettering, and HMAC signing.

```
publisher ──POST /events──> [ingest API]
                              │  (one tx: event row + outbox row)
                              ▼
                          PostgreSQL ◄──────────────┐
                              │                     │ delivery state machine
                    [outbox poller]                  │ PENDING→DELIVERED/DEAD
                              │                     │
                              ▼                     │
                            Kafka                   │
                              │                     │
                     [delivery consumer] ───────────┘
                              │ fan-out: 1 row per (event, subscription)
                              ▼
                    [delivery dispatcher]
              virtual threads · bounded concurrency
              backoff+jitter · circuit breaker
                              │ HMAC-signed POST
                              ▼
                    subscriber endpoints        (dead letters → replay API)
```

## Running locally

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| Kafka UI | http://localhost:8081 |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |

## Quick demo

```bash
# register a subscription
curl -s -X POST localhost:8080/api/v1/subscriptions \
  -H 'Content-Type: application/json' \
  -d '{"name":"demo","endpointUrl":"http://chaos-subscriber:9099/webhook","eventTypes":"*"}'

# publish an event
curl -s -X POST localhost:8080/api/v1/events \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: demo-1' \
  -d '{"eventType":"order.created","payload":{"orderId":42}}'

# same request again returns 200 with duplicate:true
# check delivery: curl -s localhost:9099/stats
```

## Tests

```bash
mvn verify -DskipITs   # unit tests only
mvn verify             # unit + integration (Testcontainers with real Postgres and Kafka)
```

## Chaos subscriber

A configurable failing endpoint for testing retry and dead-letter behavior.

```bash
CHAOS_FAIL_RATE=0.3 docker compose up chaos-subscriber   # 30% of requests return 500
```

`GET /stats` returns delivery counts and duplicate tracking.

## Design decisions

ADRs in [docs/adr](docs/adr):
- [Why Kafka](docs/adr/0001-why-kafka.md)
- [Delivery semantics](docs/adr/0002-delivery-semantics.md)
- [Ordering](docs/adr/0003-ordering.md)

## Known limitations

- Subscription secrets stored in plaintext (would hash/encrypt in production)
- Circuit breaker state is in-memory per instance
- Single-broker Kafka in the dev compose setup
