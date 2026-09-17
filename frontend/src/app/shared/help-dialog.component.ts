import { Component, ElementRef, input, viewChild } from '@angular/core';
import { PageGuide } from './guide';

/** "How to use" button + native <dialog> for one page's guide. */
@Component({
  selector: 'app-help',
  template: `
    <button class="ghost sm help-btn" type="button" (click)="open()">
      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true">
        <circle cx="8" cy="8" r="6.5"/><path d="M6.2 6.3a1.9 1.9 0 1 1 2.7 1.7c-.6.3-.9.7-.9 1.3M8 11.6v.2"/>
      </svg>
      How to use
    </button>

    <dialog #dlg class="help-dialog" (click)="backdrop($event)">
      <div class="help-body">
        <header class="help-head">
          <h3>How to use · {{ guide().title }}</h3>
          <button class="toast-close" type="button" aria-label="Close" (click)="close()">×</button>
        </header>
        <p class="help-summary">{{ guide().summary }}</p>
        <ol class="help-steps">
          @for (s of guide().steps; track $index) { <li>{{ s }}</li> }
        </ol>
        @if (guide().tryIt; as t) {
          <div class="help-try"><span class="tile-label">Try it</span><p>{{ t }}</p></div>
        }
      </div>
    </dialog>
  `,
})
export class HelpDialogComponent {
  guide = input.required<PageGuide>();
  private dlg = viewChild.required<ElementRef<HTMLDialogElement>>('dlg');

  open() { this.dlg().nativeElement.showModal(); }
  close() { this.dlg().nativeElement.close(); }
  backdrop(e: MouseEvent) { if (e.target === this.dlg().nativeElement) this.close(); }
}
