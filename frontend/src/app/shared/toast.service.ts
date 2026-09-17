import { Injectable, signal } from '@angular/core';

export type ToastKind = 'error' | 'success' | 'info';
export interface Toast { id: number; kind: ToastKind; text: string; }

/** App-wide transient notifications. Rendered once by <app-toasts> in the shell. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private seq = 0;

  show(kind: ToastKind, text: string, ttlMs = kind === 'error' ? 7000 : 3500) {
    const id = ++this.seq;
    this.toasts.update((t) => [...t, { id, kind, text }]);
    setTimeout(() => this.dismiss(id), ttlMs);
  }
  error(text: string) { this.show('error', text); }
  success(text: string) { this.show('success', text); }
  info(text: string) { this.show('info', text); }

  dismiss(id: number) {
    this.toasts.update((t) => t.filter((x) => x.id !== id));
  }
}
