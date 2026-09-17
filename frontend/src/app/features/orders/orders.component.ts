import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { ToastService } from '../../shared/toast.service';
import { InfoComponent } from '../../shared/info.component';
import { HelpDialogComponent } from '../../shared/help-dialog.component';
import { PAGE_GUIDES } from '../../shared/guide';
import { Order, OrderEvent, OrderStatus } from '../../core/models';

/** §14.1 order/state visibility: filterable list + the §6.1 transition timeline for one order. */
@Component({
  selector: 'app-orders',
  imports: [FormsModule, DatePipe, InfoComponent, HelpDialogComponent],
  template: `
    <header class="page-head">
      <div>
        <h2>Orders <app-info text="Every reservation and where it sits in the state machine. Reserved orders can be paid or cancelled here." /></h2>
      </div>
      <app-help [guide]="guide" />
    </header>

    <div class="split">
      <div class="surface">
        <form class="toolbar" (submit)="$event.preventDefault(); load()">
          <label for="f-status">Status
            <select id="f-status" name="status" [(ngModel)]="status" (change)="load()">
              <option [ngValue]="''">Any</option>
              @for (s of statuses; track s) { <option [ngValue]="s">{{ s }}</option> }
            </select>
          </label>
          <label for="f-customer">Customer
            <input id="f-customer" name="customerId" class="mono" [(ngModel)]="customerId" placeholder="customer uuid" style="width: 300px" />
          </label>
          <span class="spacer"></span>
          <span class="hint">{{ orders().length }} shown</span>
          <button class="ghost sm" type="submit">Search</button>
        </form>
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>Order</th><th>Customer</th><th class="num">Qty</th><th>Status</th><th>Reservation expires</th><th></th></tr>
            </thead>
            <tbody>
              @for (o of orders(); track o.id) {
                <tr>
                  <td class="mono" [title]="o.id">{{ o.id.slice(0, 8) }}</td>
                  <td class="mono muted" [title]="o.customerId">{{ o.customerId.slice(0, 8) }}</td>
                  <td class="num">{{ o.qty }}</td>
                  <td><span class="pill {{ o.status }}">{{ o.status }}</span></td>
                  <td class="mono muted">{{ o.reservationExpiresAt ? (o.reservationExpiresAt | date: 'HH:mm:ss') : '—' }}</td>
                  <td class="actions">
                    <div class="row">
                      <button class="ghost sm" (click)="select(o)">Timeline</button>
                      @if (o.status === 'RESERVED') {
                        <button class="sm" (click)="pay(o)">Pay</button>
                        <button class="danger sm" (click)="cancel(o)">Cancel</button>
                      }
                    </div>
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="empty">No orders match this filter.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </div>

      @if (selected(); as sel) {
        <aside class="surface">
          <div class="drawer-head">
            <h3><span class="mono">{{ sel.id.slice(0, 8) }}</span> <span class="pill {{ sel.status }}">{{ sel.status }}</span></h3>
            <button class="ghost sm" (click)="selected.set(null)">Close</button>
          </div>
          <ol class="timeline">
            @for (e of events(); track e.id) {
              <li>
                <time>{{ e.createdAt | date: 'yyyy-MM-dd HH:mm:ss' }}</time>
                <span class="pill {{ e.eventType }}">{{ e.eventType }}</span>
                @if (e.payload) { <pre>{{ e.payload }}</pre> }
              </li>
            } @empty {
              <li class="muted">No events recorded.</li>
            }
          </ol>
        </aside>
      }
    </div>
  `,
})
export class OrdersComponent {
  private api = inject(ApiService);
  private toast = inject(ToastService);
  guide = PAGE_GUIDES.find((g) => g.key === 'orders')!;
  statuses: OrderStatus[] = ['PENDING', 'RESERVED', 'PAID', 'EXPIRED', 'CANCELLED'];
  status: OrderStatus | '' = '';
  customerId = '';
  orders = signal<Order[]>([]);
  selected = signal<Order | null>(null);
  events = signal<OrderEvent[]>([]);

  constructor() {
    this.load();
  }

  load() {
    this.api
      .listOrders({
        status: this.status || undefined,
        customerId: this.customerId.trim() || undefined,
      })
      .subscribe({
        next: (o) => this.orders.set(o),
        error: (e) => this.toast.error('Could not load orders: ' + (e.error?.detail ?? e.message ?? e)),
      });
  }

  select(o: Order) {
    this.selected.set(o);
    this.api.orderEvents(o.id).subscribe({
      next: (ev) => this.events.set(ev),
      error: (e) => this.toast.error('Could not load timeline: ' + (e.error?.detail ?? e.message ?? e)),
    });
  }

  pay(o: Order) {
    this.api.payOrder(o.id).subscribe({ next: () => { this.toast.success(`Order ${o.id.slice(0, 8)} paid`); this.refreshAfter(o); }, error: (e) => this.opErr(e) });
  }

  cancel(o: Order) {
    this.api.cancelOrder(o.id).subscribe({ next: () => { this.toast.info(`Order ${o.id.slice(0, 8)} cancelled`); this.refreshAfter(o); }, error: (e) => this.opErr(e) });
  }

  private refreshAfter(o: Order) {
    this.load();
    if (this.selected()?.id === o.id) this.select(o);
  }

  private opErr(e: any) {
    this.toast.error('Action failed: ' + (e.error?.detail ?? e.message ?? e));
  }
}
