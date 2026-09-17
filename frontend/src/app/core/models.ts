export type OrderStatus = 'PENDING' | 'RESERVED' | 'PAID' | 'EXPIRED' | 'CANCELLED';

export interface InventoryRow {
  id: string;
  sku: string;
  name: string;
  price: number;
  imageUrl: string | null;
  redisStock: number;
  pgAvailableQty: number;
  pgReservedQty: number;
}

export interface Order {
  id: string;
  customerId: string;
  productId: string;
  qty: number;
  status: OrderStatus;
  reservationExpiresAt: string | null;
}

export interface OrderEvent {
  id: string;
  eventType: string;
  payload: string;
  createdAt: string;
}

export interface CircuitBreakerStatus {
  name: string;
  state: 'CLOSED' | 'OPEN' | 'HALF_OPEN';
  failureRate: number;
  bufferedCalls: number;
  failedCalls: number;
}

export interface RateLimiterStatus {
  capacity: number;
  refillPerSecond: number;
  redisReachable: boolean;
  failOpen: boolean;
}

export interface AdminStatus {
  reservationWrite: CircuitBreakerStatus;
  queuedReservationDepth: number;
  rateLimiter: RateLimiterStatus;
}

export interface Health {
  status: string;
  components?: Record<string, { status: string }>;
}
