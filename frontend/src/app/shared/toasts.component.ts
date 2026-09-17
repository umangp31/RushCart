import { Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toasts',
  template: `
    <div class="toasts" aria-live="polite">
      @for (t of svc.toasts(); track t.id) {
        <div class="toast {{ t.kind }}" role="status">
          <span class="toast-text">{{ t.text }}</span>
          <button class="toast-close" type="button" aria-label="Dismiss" (click)="svc.dismiss(t.id)">×</button>
        </div>
      }
    </div>
  `,
})
export class ToastsComponent {
  svc = inject(ToastService);
}
