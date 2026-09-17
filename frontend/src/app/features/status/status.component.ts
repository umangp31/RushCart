import { Component, inject, signal } from '@angular/core';
import { PercentPipe } from '@angular/common';
import { EMPTY, catchError, interval, startWith, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../shared/toast.service';
import { InfoComponent } from '../../shared/info.component';
import { HelpDialogComponent } from '../../shared/help-dialog.component';
import { PAGE_GUIDES } from '../../shared/guide';
import { AdminStatus } from '../../core/models';

/** §14.1 resilience panel: Resilience4j breaker state, queued-reservation depth (§8.2), limiter posture (§7). */
@Component({
  selector: 'app-status',
  imports: [PercentPipe, InfoComponent, HelpDialogComponent],
  template: `
    <header class="page-head">
      <div>
        <h2>Resilience <app-info text="Circuit breaker on the Postgres write path, the queue it falls back to, and the edge rate limiter." /></h2>
      </div>
      <div class="row">
  <span class="meta">live · every 3s</span>
        <app-help [guide]="guide" />
      </div>
    </header>

    @if (status(); as s) {
      <div class="tiles">
        <div class="surface tile">
          <div class="tile-label">Circuit breaker · {{ s.reservationWrite.name }}</div>
          <div class="tile-value"><span class="pill {{ s.reservationWrite.state }}">{{ s.reservationWrite.state }}</span></div>
          <div class="tile-note">
            Failure rate
            {{ s.reservationWrite.failureRate < 0 ? 'n/a' : (s.reservationWrite.failureRate / 100 | percent: '1.0-1') }}
            · {{ s.reservationWrite.failedCalls }} of {{ s.reservationWrite.bufferedCalls }} recent calls failed
          </div>
        </div>

        <div class="surface tile">
          <div class="tile-label">Queued reservations</div>
          <div class="tile-value" [class.text-warn]="s.queuedReservationDepth > 0">{{ s.queuedReservationDepth }}</div>
          <div class="tile-note">Buffered while the breaker is open; drained to Postgres once it closes</div>
        </div>

        <div class="surface tile">
          <div class="tile-label">Rate limiter</div>
          <div class="tile-value">
            <span class="pill" [class.bad]="s.rateLimiter.failOpen" [class.good]="!s.rateLimiter.failOpen">
              {{ s.rateLimiter.failOpen ? 'FAIL-OPEN' : 'ENFORCING' }}
            </span>
          </div>
          <div class="tile-note">
            Token bucket · {{ s.rateLimiter.capacity }} capacity · {{ s.rateLimiter.refillPerSecond }}/s refill ·
            Redis {{ s.rateLimiter.redisReachable ? 'reachable' : 'unreachable' }}
          </div>
        </div>
      </div>
    } @else {
      <p class="muted">Loading…</p>
    }
  `,
})
export class StatusComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  guide = PAGE_GUIDES.find((g) => g.key === 'status')!;
  status = signal<AdminStatus | null>(null);
  private failed = false;

  constructor() {
    interval(3000)
      .pipe(
        startWith(0),
        switchMap(() =>
          this.api.adminStatus().pipe(
            catchError((e) => {
              if (!this.failed) this.toast.error('Could not load status: ' + (e.message ?? e));
              this.failed = true;
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((s) => {
        this.status.set(s);
        this.failed = false;
      });
  }
}
