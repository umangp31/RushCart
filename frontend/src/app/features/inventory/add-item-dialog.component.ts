import { Component, ElementRef, inject, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../shared/toast.service';

/** "Add item" button + modal form that creates a product via POST /api/v1/products. */
@Component({
  selector: 'app-add-item',
  imports: [FormsModule],
  template: `
    <button type="button" (click)="open()">+ Add item</button>

    <dialog #dlg class="help-dialog" (click)="backdrop($event)">
      <form class="help-body add-form" (submit)="$event.preventDefault(); submit()">
        <header class="help-head">
          <h3>Add item</h3>
          <button class="toast-close" type="button" aria-label="Close" (click)="close()">×</button>
        </header>

        <div class="field-grid">
          <label for="add-sku">SKU
            <input id="add-sku" name="sku" class="mono" [(ngModel)]="sku" required placeholder="SNEAKER-LTD-002" autocomplete="off" />
          </label>
          <label for="add-name">Name
            <input id="add-name" name="name" [(ngModel)]="name" required placeholder="Limited Edition Sneaker II" />
          </label>
          <label for="add-price">Price
            <input id="add-price" name="price" type="number" min="0" step="0.01" [(ngModel)]="price" required placeholder="129.99" />
          </label>
          <label for="add-qty">Starting stock
            <input id="add-qty" name="initialQty" type="number" min="0" step="1" [(ngModel)]="initialQty" required placeholder="100" />
          </label>
          <label for="add-image" class="wide">Image URL <span class="hint">optional — placeholder is used if blank</span>
            <input id="add-image" name="imageUrl" type="url" [(ngModel)]="imageUrl" placeholder="https://…/sneaker.jpg" />
          </label>
        </div>

        <footer class="add-foot">
          <span class="hint">Creates the Postgres row, the inventory row, and seeds the Redis stock counter.</span>
          <div class="row">
            <button class="ghost" type="button" (click)="close()">Cancel</button>
            <button type="submit" [disabled]="busy() || !valid()">{{ busy() ? 'Adding…' : 'Add item' }}</button>
          </div>
        </footer>
      </form>
    </dialog>
  `,
  styles: [`
    .add-form { width: min(560px, calc(100vw - 32px)); }
    .field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 14px; margin: 6px 0 16px; }
    .field-grid label { display: flex; flex-direction: column; gap: 5px; font-size: 12px; font-weight: 500; color: var(--muted); }
    .field-grid label.wide { grid-column: 1 / -1; }
    .field-grid .hint { font-weight: 400; }
    .field-grid input { width: 100%; }
    .add-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
    @media (max-width: 520px) { .field-grid { grid-template-columns: 1fr; } }
  `],
})
export class AddItemDialogComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  private dlg = viewChild.required<ElementRef<HTMLDialogElement>>('dlg');

  /** Emits after a product is created so the parent can reload. */
  created = output<string>();

  sku = ''; name = ''; price: number | null = null; initialQty: number | null = null; imageUrl = '';
  busy = signal(false);

  valid() {
    return this.sku.trim() !== '' && this.name.trim() !== '' && this.price !== null && this.price >= 0 && this.initialQty !== null && this.initialQty >= 0;
  }
  open() { this.dlg().nativeElement.showModal(); }
  close() { this.dlg().nativeElement.close(); }
  backdrop(e: MouseEvent) { if (e.target === this.dlg().nativeElement) this.close(); }

  submit() {
    if (!this.valid() || this.busy()) return;
    this.busy.set(true);
    const sku = this.sku.trim().toUpperCase();
    this.api
      .createProduct({ sku, name: this.name.trim(), price: Number(this.price), initialQty: Number(this.initialQty), imageUrl: this.imageUrl.trim() || null })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.toast.success(`Added ${sku}`);
          this.sku = ''; this.name = ''; this.price = null; this.initialQty = null; this.imageUrl = '';
          this.close();
          this.created.emit(sku);
        },
        error: (e) => {
          this.busy.set(false);
          this.toast.error('Could not add item: ' + (e.error?.detail ?? e.message ?? e));
        },
      });
  }
}
