import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { EMPTY, catchError, interval, startWith, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../shared/toast.service';
import { InfoComponent } from '../../shared/info.component';
import { HelpDialogComponent } from '../../shared/help-dialog.component';
import { PAGE_GUIDES } from '../../shared/guide';

interface Sample {
  name: string;
  labels: Record<string, string>;
  value: number;
}

/** §14.1 thin read-only view over /actuator/prometheus — not a Grafana replacement (§14.1). */
@Component({
  selector: 'app-metrics',
  template: `
    <header class="page-head">
      <div>
        <h2>Metrics <app-info text="A thin read over /actuator/prometheus. For history and alerting, scrape it into Prometheus." /></h2>
      </div>
      <div class="row">
  <span class="meta">live · every 5s</span>
        <app-help [guide]="guide" />
      </div>
    </header>

    <div class="tiles">
      <div class="surface tile">
        <div class="tile-label">Reservation outcomes</div>
        @for (o of reservationOutcomes(); track o.name) {
          <div class="tile-row"><span>{{ o.name }}</span><b>{{ o.value | number }}</b></div>
        } @empty { <div class="tile-note">No reservations yet</div> }
      </div>

      <div class="surface tile">
        <div class="tile-label">Breaker calls</div>
        @for (o of breakerCalls(); track o.name) {
          <div class="tile-row"><span>{{ o.name }}</span><b>{{ o.value | number }}</b></div>
        } @empty { <div class="tile-note">No calls recorded</div> }
      </div>

      <div class="surface tile">
        <div class="tile-label">Kafka consumer lag</div>
        <div class="tile-value">{{ kafkaLag() ?? '—' }}</div>
        <div class="tile-note">Max records lag across partitions</div>
      </div>

      <div class="surface tile">
        <div class="tile-label">Lua script calls</div>
        <div class="tile-value">{{ luaCount() === null ? '—' : (luaCount() | number) }}</div>
        <div class="tile-note">Stock decrement + rate-limit scripts</div>
      </div>
    </div>

    <div class="section-title">All samples</div>
    <div class="surface">
      <div class="toolbar">
        <input id="metric-filter" name="filter" class="mono" [(ngModel)]="filter" placeholder="Filter metric names…" style="flex: 1; max-width: 420px" />
        <span class="spacer"></span>
        <span class="hint">{{ filtered().length }} of {{ samples().length }}</span>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>Metric</th><th class="num">Value</th></tr></thead>
          <tbody>
            @for (s of filtered(); track $index) {
              <tr>
                <td class="mono">{{ s.name }}<span class="muted">{{ labelStr(s.labels) }}</span></td>
                <td class="num">{{ s.value }}</td>
              </tr>
            } @empty {
              <tr><td colspan="2" class="empty">No metrics match.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
  imports: [FormsModule, DecimalPipe, InfoComponent, HelpDialogComponent],
})
export class MetricsComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  guide = PAGE_GUIDES.find((g) => g.key === 'metrics')!;
  samples = signal<Sample[]>([]);
  private failed = false;
  filter = '';

  reservationOutcomes = computed(() =>
    this.samples()
      .filter((s) => s.name === 'rushcart_reservation_outcome_total')
      .map((s) => ({ name: s.labels['result'] ?? '?', value: s.value })),
  );

  breakerCalls = computed(() =>
    this.samples()
      .filter((s) => s.name === 'resilience4j_circuitbreaker_calls_total' || s.name === 'resilience4j_circuitbreaker_state')
      .map((s) => ({ name: (s.labels['kind'] ?? s.labels['state'] ?? '?'), value: s.value }))
      .filter((o) => o.value > 0),
  );

  kafkaLag = computed(() => {
    const lags = this.samples().filter((s) => s.name.includes('kafka') && s.name.includes('lag'));
    return lags.length ? Math.max(...lags.map((s) => s.value)) : null;
  });

  luaCount = computed(() => {
    const c = this.samples().filter(
      (s) => s.name.includes('rushcart') && s.name.includes('latency') && s.name.endsWith('_count'),
    );
    return c.length ? c.reduce((a, s) => a + s.value, 0) : null;
  });

  filtered = computed(() => {
    const f = this.filter.trim().toLowerCase();
    const rows = f ? this.samples().filter((s) => s.name.toLowerCase().includes(f)) : this.samples();
    return rows.slice(0, 200);
  });

  constructor() {
    interval(5000)
      .pipe(
        startWith(0),
        switchMap(() =>
          this.api.prometheus().pipe(
            catchError((e) => {
              if (!this.failed) this.toast.error('Could not scrape metrics: ' + (e.message ?? e));
              this.failed = true;
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((text) => {
        this.samples.set(this.parse(text));
        this.failed = false;
      });
  }

  labelStr(labels: Record<string, string>): string {
    const entries = Object.entries(labels);
    return entries.length ? `{${entries.map(([k, v]) => `${k}="${v}"`).join(',')}}` : '';
  }

  private parse(text: string): Sample[] {
    const out: Sample[] = [];
    for (const line of text.split('\n')) {
      if (!line || line.startsWith('#')) continue;
      const m = line.match(/^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{([^}]*)\})?\s+([0-9eE+.\-]+)$/);
      if (!m) continue;
      const labels: Record<string, string> = {};
      if (m[3]) {
        for (const pair of m[3].split(',')) {
          const lm = pair.match(/([a-zA-Z0-9_]+)="(.*?)"/);
          if (lm) labels[lm[1]] = lm[2];
        }
      }
      const value = Number(m[4]);
      if (!Number.isNaN(value)) out.push({ name: m[1], labels, value });
    }
    return out;
  }
}
