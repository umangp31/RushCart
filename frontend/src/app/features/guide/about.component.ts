import { Component } from '@angular/core';

/** What RushCart is, how a reservation flows through it, and what the project demonstrates. */
@Component({
  selector: 'app-about',
  template: `
    <div class="guide">
      <section>
        <h3>The problem</h3>
        <p>
          A flash sale puts thousands of buyers on one scarce SKU in the same second. A naive
          <span class="mono">read stock → check → write stock − 1</span> lets two requests read the same number and both
          succeed — the shop sells 52 of 50. RushCart is a backend built to make that impossible, and to
          stay up while it does it.
        </p>
      </section>

      <section>
        <h3>How one reservation flows</h3>
        <ol class="flow">
          <li><span class="flow-step">Rate limiter</span><span>Redis token bucket per <span class="mono">X-Api-Key</span> — 20 tokens, 5/s refill. Fails open if Redis is down.</span></li>
          <li><span class="flow-step">Redis Lua decrement</span><span>One atomic script checks and decrements stock. Two requests can't both win the last unit — Redis runs scripts serially.</span></li>
          <li><span class="flow-step">Postgres order</span><span>Order row written with pessimistic locking. Wrapped in a circuit breaker: if Postgres is failing, the request is accepted (202) and queued instead of erroring — stock stays held.</span></li>
          <li><span class="flow-step">Kafka OrderCreated</span><span>Published only after the DB commit. Exactly one event per successful reservation.</span></li>
          <li><span class="flow-step">Fulfillment consumer</span><span>Idempotent: a redelivered event can't pay an order twice. Keys live in Redis with a Postgres backstop.</span></li>
          <li><span class="flow-step">Rollback worker</span><span>Reservations nobody paid for expire and their stock goes back to Redis.</span></li>
        </ol>
      </section>

      <section>
        <h3>What you get</h3>
        <div class="tiles">
          <div class="surface tile">
            <div class="tile-label">Oversell</div>
            <div class="tile-value text-good">0</div>
            <div class="tile-note">2 000 concurrent HTTP requests vs 50 units → exactly 50 succeed, every run</div>
          </div>
          <div class="surface tile">
            <div class="tile-label">Reservation latency</div>
            <div class="tile-value">81 <span class="tile-unit">ms p95</span></div>
            <div class="tile-note">405 ms p99 under that same 2 000-request burst, on a laptop</div>
          </div>
          <div class="surface tile">
            <div class="tile-label">Double fulfillment</div>
            <div class="tile-value text-good">0</div>
            <div class="tile-note">Duplicate Kafka delivery → one PAID transition</div>
          </div>
          <div class="surface tile">
            <div class="tile-label">Postgres outage</div>
            <div class="tile-value">202</div>
            <div class="tile-note">Reservations queue instead of failing; drained with zero loss on recovery</div>
          </div>
        </div>
        <p>
          Every number above is a Testcontainers test in <span class="mono">./mvnw verify</span> — real Postgres, Redis and Kafka,
          nothing mocked. The <b>Simulate</b> tab lets you reproduce the first one from your browser.
        </p>
      </section>

      <section>
        <h3>Stack</h3>
        <table class="guide-table">
          <tbody>
            <tr><td>Service</td><td>Java 21 · Spring Boot 3 · virtual threads</td></tr>
            <tr><td>Stock &amp; locks</td><td>Redis 7 · Lua scripts · Redisson</td></tr>
            <tr><td>System of record</td><td>PostgreSQL 16 · Flyway · JPA with pessimistic locks</td></tr>
            <tr><td>Events</td><td>Kafka (KRaft) · idempotent consumer</td></tr>
            <tr><td>Resilience</td><td>Resilience4j circuit breaker · queued fallback</td></tr>
            <tr><td>Observability</td><td>Micrometer → Prometheus · springdoc OpenAPI</td></tr>
            <tr><td>This dashboard</td><td>Angular 19 · standalone components · reads the same <span class="mono">/api/v1</span></td></tr>
          </tbody>
        </table>
      </section>
    </div>
  `,
  styles: [`
    .flow { list-style: none; margin: 10px 0 0; padding: 0; display: flex; flex-direction: column; }
    .flow li { display: grid; grid-template-columns: 180px 1fr; gap: 16px; padding: 12px 0; border-bottom: 1px solid var(--border); color: var(--muted); font-size: 13px; }
    .flow li:last-child { border-bottom: 0; }
    .flow-step { font-weight: 600; color: var(--text); font-size: 13px; }
    .tile-unit { font: 500 13px var(--sans); color: var(--muted); }
    @media (max-width: 600px) { .flow li { grid-template-columns: 1fr; gap: 4px; } }
  `],
})
export class AboutComponent {}
