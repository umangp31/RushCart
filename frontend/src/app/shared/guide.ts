/** Per-page "How to use" copy. Shown in the help dialog on each tab and collected on the Guide tab. */
export interface PageGuide {
  key: 'inventory' | 'orders' | 'status' | 'metrics' | 'simulate';
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
      'Add item opens a form for a new SKU (name, price, starting stock, optional image URL). Items without an image get a placeholder card.',
      'Switch between Cards and Table with the toggle in the header — same data, cards for browsing, the table for comparing numbers.',
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
  {
    key: 'simulate',
    title: 'Simulate',
    summary:
      'Fires N real concurrent reservations from your browser against one SKU and tallies what the API answered — the oversell test, live.',
    steps: [
      'Pick a SKU with a small stock (replenish one on Inventory if everything is sold out) and a request count larger than that stock.',
      'Run flash sale. The verdict compares successful 201s against the stock the run started with — it can never exceed it.',
      'Each request uses its own X-Api-Key so the rate limiter stays out of the way. Tick Share one API key to watch the 20-token bucket turn the surplus into 429s instead.',
      'Cross-check on Orders (new RESERVED rows) and Inventory (stock dropped by exactly the number of successes).',
    ],
    tryIt: 'Replenish FLASH-SCARCE-001 to 5, run 30 requests: expect 5 × 201 and 25 × 409.',
  },
];
