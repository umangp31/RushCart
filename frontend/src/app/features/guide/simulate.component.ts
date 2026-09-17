import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { catchError, forkJoin, map, of } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { InventoryRow } from '../../core/models';
import { ToastService } from '../../shared/toast.service';

interface Tally { code: number; label: string; count: number; kind: 'good' | 'info' | 'warn' | 'bad'; }
interface RunResult {
  sku: string; requests: number; stockBefore: number; stockAfter: number;
  elapsedMs: number; tallies: Tally[]; succeeded: number; oversold: boolean;
}

/** Fires N concurrent reservations from the browser and tallies what the API answered. */
@Component({
  selector: 'app-simulate',
  imports: [FormsModule, DecimalPipe],
  template: `
    <div class="sim">
      <p class="muted" style="max-width: 65ch; margin: 0 0 16px">
        This fires real <span class="mono">POST /api/v1/orders</span> calls from your browser, all at once,
        and counts the answers. Whatever the numbers, successes can never exceed the stock the run started with.
      </p>

      <form class="surface sim-form" (submit)="$event.preventDefault(); run()">
        <label for="sim-sku">SKU
          <select id="sim-sku" name="sku" [(ngModel)]="sku">
            @for (r of rows(); track r.sku) {
              <option [ngValue]="r.sku">{{ r.sku }} · {{ r.redisStock }} in stock</option>
            }
          </select>
        </label>
        <label for="sim-n">Concurrent requests
          <input id="sim-n" name="n" type="number" min="1" max="200" [(ngModel)]="n" style="width: 90px" />
        </label>
        <label class="check">
          <input id="sim-onekey" name="onekey" type="checkbox" [(ngModel)]="oneKey" />
          Share one API key <span class="hint">(shows the rate limiter: 20-token bucket → 429s)</span>
        </label>
        <span class="spacer"></span>
        <button type="submit" [disabled]="running() || !sku">{{ running() ? 'Running…' : 'Run flash sale' }}</button>
      </form>

      @if (result(); as r) {
        <div class="sim-verdict surface" [class.ok]="!r.oversold" [class.bad]="r.oversold">
          <div class="tile-value">
            <span class="pill" [class.good]="!r.oversold" [class.bad]="r.oversold">{{ r.oversold ? 'OVERSOLD' : 'NO OVERSELL' }}</span>
            <span>{{ r.succeeded }} of {{ r.requests }} got stock</span>
          </div>
          <div class="tile-note">
            {{ r.sku }} · stock {{ r.stockBefore }} → {{ r.stockAfter }} · {{ r.elapsedMs | number: '1.0-0' }} ms wall-clock for the whole burst
          </div>
        </div>

        <div class="tiles">
          @for (t of r.tallies; track t.code) {
            <div class="surface tile">
              <div class="tile-label">{{ t.code }} · {{ t.label }}</div>
              <div class="tile-value" [class.text-good]="t.kind === 'good'" [class.text-bad]="t.kind === 'bad'" [class.text-warn]="t.kind === 'warn'">{{ t.count }}</div>
              <div class="tile-note">{{ explain[t.code] }}</div>
            </div>
          }
        </div>

        <p class="hint" style="margin-top: 14px">
          Cross-check: Orders now lists {{ r.succeeded }} new RESERVED orders; Inventory shows {{ r.stockAfter }} left in Redis and
          {{ r.succeeded }} more reserved in Postgres. They expire and release stock automatically if never paid.
        </p>
      }
    </div>
  `,
  styles: [`
    .sim-form { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; padding: 14px 16px; margin-bottom: 16px; }
    .sim-form label { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--muted); }
    .sim-form .check { gap: 6px; }
    .sim-form .spacer { flex: 1; }
    .sim-verdict { padding: 16px 18px; margin-bottom: 12px; border-left: 3px solid var(--good); }
    .sim-verdict.bad { border-left-color: var(--bad); }
    .sim-verdict .tile-value { font-size: 18px; font-family: var(--sans); font-weight: 500; }
  `],
})
export class SimulateComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);

  rows = signal<InventoryRow[]>([]);
  sku = '';
  n = 25;
  oneKey = false;
  running = signal(false);
  result = signal<RunResult | null>(null);

  explain: Record<number, string> = {
    201: 'Reserved — the Lua script found stock and decremented it atomically',
    409: 'Insufficient stock — rejected by Redis before touching Postgres',
    202: 'Queued — the Postgres breaker is open; stock is held and the order drains later',
    429: 'Rate limited — this client exhausted its token bucket',
    0: 'Network error — the API was unreachable',
  };

  constructor() {
    this.api.listInventory().subscribe({
      next: (r) => {
        this.rows.set(r);
        this.sku ||= r.find((x) => x.redisStock > 0 && x.redisStock < 50)?.sku ?? r[0]?.sku ?? '';
      },
      error: () => this.toast.error('Could not load products for the simulation'),
    });
  }

  run() {
    const count = Math.max(1, Math.min(200, Number(this.n) || 1));
    const before = this.rows().find((r) => r.sku === this.sku)?.redisStock ?? 0;
    const runId = Date.now().toString(36);
    this.running.set(true);
    this.result.set(null);
    const t0 = performance.now();

    const calls = Array.from({ length: count }, (_, i) =>
      this.api
        .reserve({ customerId: crypto.randomUUID(), sku: this.sku, qty: 1 }, this.oneKey ? `sim-${runId}` : `sim-${runId}-${i}`)
        .pipe(
          map((res) => res.status),
          catchError((e) => of(Number(e?.status) || 0)),
        ),
    );

    forkJoin(calls).subscribe((codes) => {
      const elapsedMs = performance.now() - t0;
      const counts = new Map<number, number>();
      for (const c of codes) counts.set(c, (counts.get(c) ?? 0) + 1);
      const meta: Record<number, [string, Tally['kind']]> = {
        201: ['Reserved', 'good'], 409: ['Out of stock', 'info'], 202: ['Queued', 'warn'], 429: ['Rate limited', 'warn'], 0: ['Error', 'bad'],
      };
      const tallies: Tally[] = [...counts.entries()]
        .sort((a, b) => b[1] - a[1])
        .map(([code, n]) => ({ code, count: n, label: meta[code]?.[0] ?? 'Other', kind: meta[code]?.[1] ?? 'bad' }));
      const succeeded = counts.get(201) ?? 0;

      this.api.listInventory().subscribe((rows) => {
        this.rows.set(rows);
        const after = rows.find((r) => r.sku === this.sku)?.redisStock ?? 0;
        this.result.set({ sku: this.sku, requests: count, stockBefore: before, stockAfter: after, elapsedMs, tallies, succeeded, oversold: succeeded > before });
        this.running.set(false);
      });
    });
  }
}
