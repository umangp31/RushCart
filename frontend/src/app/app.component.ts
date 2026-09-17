import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { interval, startWith, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService } from './core/api.service';
import { ToastsComponent } from './shared/toasts.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastsComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  private api = inject(ApiService);
  health = signal<string>('…');

  constructor() {
    interval(10_000)
      .pipe(
        startWith(0),
        switchMap(() => this.api.health()),
        takeUntilDestroyed(),
      )
      .subscribe({
        next: (h) => this.health.set(h.status),
        error: () => this.health.set('DOWN'),
      });
  }
}
