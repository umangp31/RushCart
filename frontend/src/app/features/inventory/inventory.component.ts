import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../shared/toast.service';
import { InfoComponent } from '../../shared/info.component';
import { HelpDialogComponent } from '../../shared/help-dialog.component';
import { PAGE_GUIDES } from '../../shared/guide';
import { InventoryRow } from '../../core/models';

/** §14.1 inventory view: Redis hot-path stock vs Postgres source of truth, plus replenishment. */
@Component({
  selector: 'app-inventory',
  imports: [FormsModule, DecimalPipe, InfoComponent, HelpDialogComponent],
  template: `
    <header class="page-head">
      <div>
        <h2>Inventory <app-info text="Redis holds the hot-path stock; Postgres is the source of truth. Rows where they disagree are flagged." /></h2>
      </div>
      <div class="row">
  <button class="ghost" (click)="load()">Refresh</button>
        <app-help [guide]="guide" />
      </div>
    </header>

    <div class="surface table-wrap">
      <table>
        <thead>
          <tr>
            <th>SKU</th>
            <th>Product</th>
            <th class="num">Price</th>
            <th class="num">Redis stock</th>
            <th class="num">PG available</th>
            <th class="num">PG reserved</th>
            <th class="actions">Replenish</th>
          </tr>
        </thead>
        <tbody>
          @for (row of rows(); track row.sku) {
            <tr [class.mismatch]="row.redisStock !== row.pgAvailableQty">
              <td class="mono">{{ row.sku }}</td>
              <td>{{ row.name }}</td>
              <td class="num">{{ row.price | number: '1.2-2' }}</td>
              <td class="num" [class.text-bad]="row.redisStock === 0">
                {{ row.redisStock }}
                @if (row.redisStock !== row.pgAvailableQty) {
                  <span class="delta">{{ row.redisStock - row.pgAvailableQty > 0 ? '+' : '' }}{{ row.redisStock - row.pgAvailableQty }}</span>
                }
              </td>
              <td class="num">{{ row.pgAvailableQty }}</td>
              <td class="num muted">{{ row.pgReservedQty }}</td>
              <td class="actions">
                <form class="inline-form" (submit)="$event.preventDefault(); replenish(row)">
                  <input type="number" min="1" placeholder="qty" [id]="'qty-' + row.sku" [name]="'qty-' + row.sku" [(ngModel)]="qty[row.sku]" />
                  <button class="sm" type="submit" [disabled]="busy() === row.sku || !qty[row.sku]">
                    {{ busy() === row.sku ? 'Adding…' : 'Add' }}
                  </button>
                </form>
              </td>
            </tr>
          } @empty {
            <tr><td colspan="7" class="empty">No products yet. Seed data or POST /api/v1/products.</td></tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class InventoryComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  guide = PAGE_GUIDES.find((g) => g.key === 'inventory')!;
  rows = signal<InventoryRow[]>([]);
  busy = signal<string | null>(null);
  qty: Record<string, number> = {};

  constructor() {
    this.load();
  }

  load() {
    this.api.listInventory().subscribe({
      next: (r) => this.rows.set(r),
      error: (e) => this.toast.error('Could not load inventory: ' + (e.error?.detail ?? e.message ?? e)),
    });
  }

  replenish(row: InventoryRow) {
    const amount = Number(this.qty[row.sku]);
    if (!amount || amount < 1) return;
    this.busy.set(row.sku);
    this.api.replenish(row.sku, amount).subscribe({
      next: () => {
        this.busy.set(null);
        this.qty[row.sku] = 0;
        this.toast.success(`Added ${amount} to ${row.sku}`);
        this.load();
      },
      error: (e) => {
        this.busy.set(null);
        this.toast.error('Replenish failed: ' + (e.error?.detail ?? e.message ?? e));
      },
    });
  }
}
