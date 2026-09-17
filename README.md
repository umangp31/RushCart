# RushCart

A flash-sale order backend that **cannot oversell** — and stays up while it proves it.

Thousands of buyers hit one scarce SKU in the same second. A naive
`read stock → check → write stock − 1` lets two requests read the same number and both
succeed. RushCart is the reservation engine that makes that impossible, backed by a
concurrency test suite that fires 2 000 simultaneous requests at 50 units and asserts
exactly 50 win — against real Postgres, Redis and Kafka, nothing mocked.

| Guarantee | How it's proven |
|---|---|
| **Zero oversell** under 2 000 concurrent reservations vs 50 units | `ConcurrencyIT`, `OrderReservationConcurrencyTest` — exactly 50 × 201, rest 409 |
| **Zero double-fulfillment** on duplicate Kafka delivery | `FulfillmentConsumerTest` — redelivered event → one `PAID` transition |
| **Graceful degradation** when Postgres fails | `CircuitBreakerFallbackTest` — breaker opens → reservations return 202 and queue, drained with no loss on recovery |
| **Rate limiter fails open** when Redis is gone | `RateLimiterFailOpenTest` — Redis container killed, requests still pass |
| p95 81 ms / p99 405 ms at 2 000-request burst | `ReservationLoadPessimisticIT` |

## How a reservation flows

```
POST /api/v1/orders
  │
  ├─ 1. Rate limiter      Redis token bucket per X-Api-Key (20 tokens, 5/s). Fails open.
  ├─ 2. Lua decrement     One atomic Redis script checks + decrements stock. No two
  │                       requests can both take the last unit — scripts run serially.
  ├─ 3. Postgres order    Order row under pessimistic lock, wrapped in a Resilience4j
  │                       circuit breaker. Breaker OPEN → 202 + queued, stock stays held.
  ├─ 4. Kafka             OrderCreated published after commit — exactly one per success.
  ├─ 5. Fulfillment       Idempotent consumer (Redis key, Postgres backstop) → PAID.
  └─ 6. Rollback worker   Unpaid reservations expire; stock returns to Redis.
```

## Stack

| | |
|---|---|
| Service | Java 21 · Spring Boot 3.3 · virtual threads |
| Stock & locks | Redis 7 · Lua scripts · Redisson |
| System of record | PostgreSQL 16 · Flyway · JPA (pessimistic locking) |
| Events | Apache Kafka (KRaft) · idempotent consumer |
| Resilience | Resilience4j circuit breaker · queued-reservation fallback |
| Observability | Micrometer → Prometheus · springdoc OpenAPI |
| Tests | JUnit 5 · Testcontainers (Postgres, Redis, Kafka) · Awaitility |
| Ops dashboard | Angular 19, standalone components (`frontend/`) |

## Run it locally

Prerequisites: Docker, JDK 21+, Node 20+.

```bash
# 1. infrastructure — Postgres :5433, Redis :6380, Kafka :9092
docker compose up -d

# 2. backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
#    (port taken? add -Dspring-boot.run.arguments=--server.port=8081)

# 3. ops dashboard
cd frontend && npm install && npm start      # http://localhost:4200
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Prometheus scrape: http://localhost:8080/actuator/prometheus

Postgres and Redis are mapped to non-default host ports (5433 / 6380) so they don't
collide with local installs; the `local` profile already expects this. Override with
`DB_PORT`, `REDIS_PORT`, `KAFKA_PORT`, `DB_USER`, `DB_PASSWORD` (see `.env.example`).

Flyway seeds three SKUs on first boot:

| SKU | Stock | Purpose |
|---|---|---|
| `FLASH-SCARCE-001` | 3 | scarce item for oversell tests |
| `SNEAKER-LTD-001` | 100 | limited drop |
| `HOODIE-STD-001` | 500 | plenty of stock — control case |

## Try it

```bash
# reserve — 201 RESERVED, or 409 when stock is gone
curl -s -X POST localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' -H 'X-Api-Key: demo' \
  -d '{"customerId":"11111111-1111-1111-1111-111111111111","sku":"SNEAKER-LTD-001","qty":1}'

curl -s -X POST localhost:8080/api/v1/orders/<id>/pay      # RESERVED → PAID
curl -s -X POST localhost:8080/api/v1/orders/<id>/cancel   # RESERVED → CANCELLED, stock returned
curl -s localhost:8080/api/v1/orders/<id>/events           # audit timeline

# 20 concurrent buyers vs 3 units → exactly 3 × 201, 17 × 409
seq 20 | xargs -P 20 -I{} curl -s -o /dev/null -w '%{http_code}\n' \
  -X POST localhost:8080/api/v1/orders -H 'Content-Type: application/json' -H "X-Api-Key: k{}" \
  -d '{"customerId":"22222222-2222-2222-2222-222222222222","sku":"FLASH-SCARCE-001","qty":1}' \
  | sort | uniq -c

# break things on purpose
docker compose stop postgres    # breaker OPEN → reservations return 202 and queue
docker compose start postgres   # breaker closes, queue drains
```

The dashboard's **Guide → Simulate** tab runs the same burst from the browser and reports
the tally plus a NO OVERSELL / OVERSOLD verdict.

## API

| Method | Path | |
|---|---|---|
| `POST` | `/api/v1/orders` | reserve stock `{customerId, sku, qty}` → 201 / 409 / 202 (queued) |
| `GET` | `/api/v1/orders` | list, filter by `status`, `customerId` |
| `GET` | `/api/v1/orders/{id}` | one order |
| `GET` | `/api/v1/orders/{id}/events` | state-transition timeline |
| `POST` | `/api/v1/orders/{id}/pay` | RESERVED → PAID |
| `POST` | `/api/v1/orders/{id}/cancel` | RESERVED → CANCELLED |
| `GET` | `/api/v1/products` | catalog with Redis vs Postgres stock |
| `POST` | `/api/v1/products` | create `{sku, name, price, initialQty}` |
| `POST` | `/api/v1/products/{sku}/replenish` | add stock `{qty}` |
| `GET` | `/api/v1/admin/status` | breaker state, queue depth, limiter posture |

Errors are RFC 7807 `application/problem+json`. Rate limiting is per `X-Api-Key`
(falls back to client IP); over-budget requests get 429.

## Ops dashboard

`frontend/` — an internal Angular console, not a storefront. Inventory (Redis vs Postgres
stock, replenish), Orders (filter, timeline, pay/cancel), Resilience (breaker / queue /
limiter, live), Metrics (Prometheus scrape), and a Guide tab with a run-through and a
one-click flash-sale simulation. Light and dark themes.

## Tests

```bash
./mvnw verify          # 28 tests incl. Testcontainers integration — needs Docker
cd frontend && npm test
```

The three `*IT` load-test classes (`ReservationLoadIT`, `ReservationLoadPessimisticIT`,
`ConcurrencyIT`) are run on demand: `./mvnw test -Dtest=ConcurrencyIT`.

## Layout

```
src/main/java/com/rushcart/
  inventory/   Product, Inventory, StockService (Lua), ReplenishmentService (Redisson lock)
  order/       Order state machine, reservation flow, rollback + drain workers, Kafka producer
  payment/     Fulfillment consumer (idempotent)
  ratelimit/   Token-bucket interceptor
  common/      Idempotency keys, RFC 7807 error handling
  config/      Clock, Kafka, Redisson, OpenAPI, CORS
src/main/resources/db/   Flyway migrations + demo seed
frontend/                Angular ops dashboard
docker-compose.yml       Postgres, Redis, Kafka (KRaft)
```
