import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AdminStatus,
  Health,
  InventoryRow,
  Order,
  OrderEvent,
  OrderStatus,
} from './models';

/** Thin client over the `/api/v1` surface (§14.2 — no dashboard-side business logic). */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private base = environment.apiBase;

  listInventory(): Observable<InventoryRow[]> {
    return this.http.get<InventoryRow[]>(`${this.base}/api/v1/products`);
  }

  createProduct(body: { sku: string; name: string; price: number; initialQty: number; imageUrl?: string | null }): Observable<unknown> {
    return this.http.post(`${this.base}/api/v1/products`, body);
  }

  replenish(sku: string, qty: number): Observable<unknown> {
    return this.http.post(`${this.base}/api/v1/products/${encodeURIComponent(sku)}/replenish`, { qty });
  }

  listOrders(filter: { status?: OrderStatus; customerId?: string; limit?: number }): Observable<Order[]> {
    let params = new HttpParams();
    if (filter.status) params = params.set('status', filter.status);
    if (filter.customerId) params = params.set('customerId', filter.customerId);
    params = params.set('limit', String(filter.limit ?? 100));
    return this.http.get<Order[]>(`${this.base}/api/v1/orders`, { params });
  }

  getOrder(id: string): Observable<Order> {
    return this.http.get<Order>(`${this.base}/api/v1/orders/${id}`);
  }

  orderEvents(id: string): Observable<OrderEvent[]> {
    return this.http.get<OrderEvent[]>(`${this.base}/api/v1/orders/${id}/events`);
  }

  cancelOrder(id: string): Observable<Order> {
    return this.http.post<Order>(`${this.base}/api/v1/orders/${id}/cancel`, {});
  }

  payOrder(id: string): Observable<Order> {
    return this.http.post<Order>(`${this.base}/api/v1/orders/${id}/pay`, {});
  }

  /** Reserve stock for one order. Returns the full response so callers can read the status code. */
  reserve(body: { customerId: string; sku: string; qty: number }, apiKey: string): Observable<HttpResponse<Order>> {
    return this.http.post<Order>(`${this.base}/api/v1/orders`, body, {
      observe: 'response',
      headers: { 'X-Api-Key': apiKey },
    });
  }

  adminStatus(): Observable<AdminStatus> {
    return this.http.get<AdminStatus>(`${this.base}/api/v1/admin/status`);
  }

  health(): Observable<Health> {
    return this.http.get<Health>(`${this.base}/actuator/health`);
  }

  prometheus(): Observable<string> {
    return this.http.get(`${this.base}/actuator/prometheus`, { responseType: 'text' });
  }
}
