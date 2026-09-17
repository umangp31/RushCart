/** Per-page "How to use" copy. Shown in the help dialog on each tab and collected on the Guide tab. */
export interface PageGuide {
  key: 'inventory' | 'orders' | 'status' | 'metrics';
  title: string;
  summary: string;
  steps: string[];
  tryIt?: string;
}

export const PAGE_GUIDES: PageGuide[] = [
  {
    key: 'inventory',
    title: 'Inventory',
    summary:
      'One row per SKU. Redis stock is what the hot path decrements; Postgres available/reserved is the source of truth. A stripe and ± delta mark rows where the two disagree.',
    steps: [
      'Type a quantity in the Replenish box and press Enter (or Add) to top up a SKU — this writes to Postgres and Redis atomically under a Redisson lock.',
      'Redis stock shown in red means the SKU is sold out on the hot path; new reservations will be rejected with 409.',
      'Use Refresh after running load tests or curl commands to see the new balance.',
    ],
    tryIt: 'Add 5 to FLASH-SCARCE-001, then reserve 6 orders against it from Swagger — the sixth should be a 409.',
  },
  {
    key: 'orders',
    title: 'Orders',
    summary:
      'Every reservation and its current state: PENDING → RESERVED → PAID, or RESERVED → EXPIRED / CANCELLED.',
    steps: [
      'Filter by status or paste a customer UUID, then Search.',
      'Timeline opens a side panel with the order_events audit trail — the exact transitions and their payloads.',
      'Pay and Cancel appear only on RESERVED orders (the state machine rejects anything else with 409). Cancel returns stock to Redis immediately.',
      'Orders left RESERVED past their reservation window are expired by the rollback worker and their stock is released.',
    ],
    tryIt: 'Reserve an order from Swagger, watch it appear here as RESERVED, cancel it, then check Inventory — the Redis stock is back.',
  },
  {
    key: 'status',
    title: 'Resilience',
    summary:
      'Live view (3 s) of the circuit breaker guarding Postgres writes, the reservation queue it falls back to, and the edge rate limiter.',
    steps: [
      'CLOSED is normal. OPEN means recent writes failed above the 50% threshold; new reservations are accepted with 202 and queued instead of failing.',
      'Queued reservations turns amber while the buffer is non-empty; the drain worker replays them once the breaker closes.',
      'ENFORCING means the token bucket is live. FAIL-OPEN means Redis is unreachable and the limiter is letting traffic through rather than blocking it.',
    ],
    tryIt: 'Run `docker compose stop postgres`, reserve a few orders (expect 202), watch the breaker go OPEN and the queue fill, then `docker compose start postgres` and watch it drain.',
  },
  {
    key: 'metrics',
    title: 'Metrics',
    summary:
      'A thin read over /actuator/prometheus, refreshed every 5 s. Tiles pull out the numbers that matter for this project; the table below is the raw scrape.',
    steps: [
      'Reservation outcomes counts success / insufficient_stock / conflict / queued — under load, insufficient_stock should equal requests minus starting stock, never more successes than stock.',
      'Breaker calls and Kafka consumer lag confirm the fallback and fulfillment paths are moving.',
      'Filter the raw table by a name fragment (e.g. `rushcart_`, `hikari`, `kafka`).',
    ],
    tryIt: 'Run the load test, then compare rushcart_reservation_outcome_total{result="success"} to the starting stock.',
  },
];
