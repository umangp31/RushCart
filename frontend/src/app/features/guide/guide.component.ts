import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PAGE_GUIDES } from '../../shared/guide';
import { AboutComponent } from './about.component';

type Tab = 'about' | 'run';

/** Static how-to for running and exercising RushCart from this dashboard, Swagger, and curl. */
@Component({
  selector: 'app-guide',
  imports: [RouterLink, AboutComponent],
  template: `
    <header class="page-head">
      <div><h2>Guide</h2></div>
      <div class="subtabs" role="tablist">
        <button type="button" role="tab" [attr.aria-selected]="tab() === 'about'" [class.active]="tab() === 'about'" (click)="tab.set('about')">About</button>
        <button type="button" role="tab" [attr.aria-selected]="tab() === 'run'" [class.active]="tab() === 'run'" (click)="tab.set('run')">Run &amp; test</button>
      </div>
    </header>

    @if (tab() === 'about') { <app-about /> }

    <div class="guide" [hidden]="tab() !== 'run'">
      <section>
        <h3>1 · Bring the stack up</h3>
        <p>Everything runs locally against Docker. Three terminals, in this order:</p>
        <pre><code># infra — Postgres :5433, Redis :6380, Kafka :9092
docker compose up -d

# backend (add -Dspring-boot.run.arguments=--server.port=8081 if 8080 is taken)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# this dashboard
cd frontend && npm start</code></pre>
        <p>
          Then open <a href="http://localhost:8080/swagger-ui.html" target="_blank" rel="noopener">Swagger</a>
          for the full API, and <a href="http://localhost:8080/actuator/health" target="_blank" rel="noopener">/actuator/health</a>
          should report <span class="pill good">UP</span>. The dot in the sidebar mirrors it.
        </p>
      </section>

      <section>
        <h3>2 · Seeded catalog</h3>
        <p>Flyway seeds three SKUs on first boot so the pages are never empty:</p>
        <table class="guide-table">
          <thead><tr><th>SKU</th><th>Purpose</th><th class="num">Stock</th></tr></thead>
          <tbody>
            <tr><td class="mono">FLASH-SCARCE-001</td><td>Scarce item for oversell tests</td><td class="num">3</td></tr>
            <tr><td class="mono">SNEAKER-LTD-001</td><td>Limited drop</td><td class="num">100</td></tr>
            <tr><td class="mono">HOODIE-STD-001</td><td>Plenty of stock — control case</td><td class="num">500</td></tr>
          </tbody>
        </table>
      </section>

      <section>
        <h3>3 · Walk one order through</h3>
        <pre><code># reserve — 201 RESERVED, or 409 when stock is gone
curl -s -X POST localhost:8080/api/v1/orders \\
  -H 'Content-Type: application/json' -H 'X-Api-Key: demo' \\
  -d '{{ '{' }}"customerId":"11111111-1111-1111-1111-111111111111","sku":"SNEAKER-LTD-001","qty":1{{ '}' }}'

# then, using the id from the response
curl -s -X POST localhost:8080/api/v1/orders/&lt;id&gt;/pay        # RESERVED → PAID
curl -s -X POST localhost:8080/api/v1/orders/&lt;id&gt;/cancel     # RESERVED → CANCELLED, stock returned
curl -s localhost:8080/api/v1/orders/&lt;id&gt;/events              # audit timeline</code></pre>
        <p>
          Watch it land on <a routerLink="/orders">Orders</a>; the Timeline panel shows the same events.
          Paying or cancelling from that page calls exactly these endpoints.
        </p>
      </section>

      <section>
        <h3>4 · Prove there is no oversell</h3>
        <p>The point of the project. Fire more requests than there is stock and count the successes:</p>
        <pre><code># 20 concurrent reservations against a SKU with 3 in stock → exactly 3 × 201, 17 × 409
seq 20 | xargs -P 20 -I{{ '{' }}{{ '}' }} curl -s -o /dev/null -w '%{{ '{' }}http_code{{ '}' }}\\n' \\
  -X POST localhost:8080/api/v1/orders -H 'Content-Type: application/json' -H 'X-Api-Key: load' \\
  -d '{{ '{' }}"customerId":"22222222-2222-2222-2222-222222222222","sku":"FLASH-SCARCE-001","qty":1{{ '}' }}' | sort | uniq -c</code></pre>
        <p>
          Replenish the SKU on <a routerLink="/inventory">Inventory</a> first if it is already sold out.
          The full 2 000-request suite is <span class="mono">./mvnw verify</span> (Testcontainers; needs Docker).
          Note the rate limiter: 20 tokens, 5/s refill per <span class="mono">X-Api-Key</span> — bursts above that return 429.
        </p>
      </section>

      <section>
        <h3>5 · Break things on purpose</h3>
        <pre><code>docker compose stop postgres     # writes fail → breaker OPEN → reservations return 202 and queue
docker compose start postgres    # breaker half-opens, closes; queue drains back into Postgres

docker compose stop redis        # limiter fails open (traffic still passes); stock path errors
docker compose start redis</code></pre>
        <p>Follow it live on <a routerLink="/status">Resilience</a> and the counters on <a routerLink="/metrics">Metrics</a>.</p>
      </section>

      <section>
        <h3>Pages at a glance</h3>
        <div class="guide-cards">
          @for (g of guides; track g.key) {
            <a class="surface guide-card" [routerLink]="'/' + g.key">
              <span class="tile-label">{{ g.title }}</span>
              <p>{{ g.summary }}</p>
            </a>
          }
        </div>
        <p class="hint">Each page also has a <b>How to use</b> button in its header with step-by-step notes. To reproduce the oversell test from the browser, open <a routerLink="/simulate">Simulate</a>.</p>
      </section>
    </div>
  `,
})
export class GuideComponent {
  guides = PAGE_GUIDES;
  tab = signal<Tab>('about');
}
