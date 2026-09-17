import { Component, input } from '@angular/core';

/** Small "i" glyph that reveals a short description on hover / focus. */
@Component({
  selector: 'app-info',
  template: `
    <span class="info" tabindex="0" [attr.aria-label]="text()">
      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true">
        <circle cx="8" cy="8" r="6.5"/><path d="M8 7v4.5M8 4.8v.2"/>
      </svg>
      <span class="info-tip" role="tooltip">{{ text() }}</span>
    </span>
  `,
})
export class InfoComponent {
  text = input.required<string>();
}
