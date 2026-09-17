import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../shared/toast.service';
import { InfoComponent } from '../../shared/info.component';
import { HelpDialogComponent } from '../../shared/help-dialog.component';
import { PAGE_GUIDES } from '../../shared/guide';
import { AddItemDialogComponent } from './add-item-dialog.component';
import { gradientFor } from '../../shared/placeholder';
import { InventoryRow } from '../../core/models';

/** §14.1 inventory view: Redis hot-path stock vs Postgres source of truth, plus replenishment. */
@Component({
  selector: 'app-inventory',
  imports: [FormsModule, DecimalPipe, InfoComponent, HelpDialogComponent, AddItemDialogComponent],
  template: `
    <header class="page-head">
      <div><h2>Inventory <app-info text="Redis holds the hot-path stock; Postgres is the source of truth. Rows where they disagree are flagged." /></h2></div>
      <div class="row">
        <div class="subtabs" role="tablist" aria-label="View">
          <button type="button" role="tab" [attr.aria-selected]="view() === 'cards'" [class.active]="view() === 'cards'" (click)="setView('cards')">Cards</button>
          <button type="button" role="tab" [attr.aria-selected]="view() === 'table'" [class.active]="view() === 'table'" (click)="setView('table')">Table</button>
        </div>
        <button class="ghost" (click)="load()">Refresh</button>
        <app-add-item (created)="load()" />
        <app-help [guide]="guide" />
      </div>
    </header>

    @if (view() === 'cards') {
      <div class="cards">
        @for (row of rows(); track row.sku) {
          <article class="surface card" [class.mismatch]="row.redisStock !== row.pgAvailableQty">
            <div class="card-media" [style.background]="showImage(row) ? null : gradientFor(row.sku)">
              @if (showImage(row)) {
                <img [src]="row.imageUrl" [alt]="row.name" loading="lazy" (error)="onImgError(row.sku)" />
              } @else {
                <span class="card-initials" aria-hidden="true">{{ initials(row.name) }}</span>
              }
              @if (row.redisStock === 0) { <span class="pill bad card-flag">Sold out</span> }
              @else if (row.redisStock !== row.pgAvailableQty) { <span class="pill warn card-flag">Redis ≠ PG</span> }
            </div>
            <div class="card-body">
              <div class="card-title">
                <h3>{{ row.name }}</h3>
                <span class="card-price">{{ row.price | number: '1.2-2' }}</span>
              </div>
              <div class="mono card-sku">{{ row.sku }}</div>
              <dl class="card-stats">
                <div><dt>Redis</dt><dd [class.text-bad]="row.redisStock === 0">{{ row.redisStock }}</dd></div>
                <div><dt>PG avail</dt><dd>{{ row.pgAvailableQty }}</dd></div>
                <div><dt>Reserved</dt><dd class="muted">{{ row.pgReservedQty }}</dd></div>
              </dl>
              <form class="inline-form card-form" (submit)="$event.preventDefault(); replenish(row)">
                <input type="number" min="1" placeholder="qty" [id]="'cqty-' + row.sku" [name]="'cqty-' + row.sku" [(ngModel)]="qty[row.sku]" />
                <button class="sm" type="submit" [disabled]="busy() === row.sku || !qty[row.sku]">
                  {{ busy() === row.sku ? 'Adding…' : 'Replenish' }}
                </button>
              </form>
            </div>
          </article>
        } @empty {
          <div class="surface empty" style="grid-column: 1 / -1">No products yet. Use <b>Add item</b> to create one.</div>
        }
      </div>
    } @else {
      <div class="surface table-wrap">
        <table>
          <thead>
            <tr>
              <th></th>
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
                <td class="thumb-cell">
                  @if (showImage(row)) {
                    <img class="thumb" [src]="row.imageUrl" alt="" loading="lazy" (error)="onImgError(row.sku)" />
                  } @else {
                    <span class="thumb" [style.background]="gradientFor(row.sku)"></span>
                  }
                </td>
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
              <tr><td colspan="8" class="empty">No products yet. Use <b>Add item</b> to create one.</td></tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class InventoryComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  guide = PAGE_GUIDES.find((g) => g.key === 'inventory')!;
  rows = signal<InventoryRow[]>([]);
  busy = signal<string | null>(null);
  qty: Record<string, number> = {};
  gradientFor = gradientFor;
  private broken = signal<Set<string>>(new Set());

  showImage(row: InventoryRow) {
    return !!row.imageUrl && !this.broken().has(row.sku);
  }
  initials(name: string) {
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0]!.toUpperCase()).join('');
  }
  view = signal<'cards' | 'table'>(this.readView());

  setView(v: 'cards' | 'table') {
    this.view.set(v);
    try { localStorage.setItem('rushcart.inventory.view', v); } catch {}
  }
  private readView(): 'cards' | 'table' {
    try { return localStorage.getItem('rushcart.inventory.view') === 'table' ? 'table' : 'cards'; } catch { return 'cards'; }
  }
  onImgError(sku: string) {
    this.broken.update((b) => new Set(b).add(sku));
  }

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
