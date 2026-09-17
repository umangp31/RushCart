# RushCart Admin / Ops Dashboard

Internal Angular dashboard for the RushCart reservation engine. Read-only
plus inventory replenishment — **not** a customer storefront. Talks directly to `/api/v1`
(no BFF).

## Prerequisites

- Node 20.11+ / 22+ (Angular 19)
- The Order Service running on `http://localhost:8080` with the local infra up:
  ```
  docker compose up -d          # from repo root — Postgres, Redis, Kafka
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
  ```

## Run

```
npm install
npm start            # ng serve on http://localhost:4200, proxies /api + /actuator to :8080
```

`proxy.conf.json` handles local CORS; the backend also allows `http://localhost:4200`
directly (`rushcart.cors.allowed-origins`).

## Views

| Route | Scope | Backend |
|---|---|---|
| `/inventory` | §14.1 — Redis stock vs Postgres `available/reserved`, replenish form | `GET /api/v1/products`, `POST /api/v1/products/{sku}/replenish` |
| `/orders` | §14.1 — filterable order list + `order_events` timeline; pay/cancel | `GET /api/v1/orders`, `GET /api/v1/orders/{id}/events`, `.../pay`, `.../cancel` |
| `/status` | §14.1 — circuit-breaker state, queued-reservation depth, rate-limiter posture | `GET /api/v1/admin/status` |
| `/metrics` | §14.1 — thin view over Prometheus (reservation outcomes, breaker, Kafka lag) | `GET /actuator/prometheus` |

## Test

```
npm test             # headless Chrome via karma.conf.js
```

## Build

```
npm run build        # dist/rushcart-admin
```
