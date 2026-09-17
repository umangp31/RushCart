package com.rushcart.order;

import com.rushcart.inventory.Inventory;
import com.rushcart.inventory.InventoryRepository;
import com.rushcart.inventory.Product;
import com.rushcart.inventory.ProductNotFoundException;
import com.rushcart.inventory.ProductRepository;
import com.rushcart.inventory.StockReservationResult;
import com.rushcart.inventory.StockService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Duration RESERVATION_WINDOW = Duration.ofMinutes(5);

    /**
     * §6.2: under a burst of already-Redis-approved requests for the same SKU, the shared
     * inventory row is optimistically-locked contention; a handful of retries absorbs that
     * without falling back to pessimistic locking on the hot path.
     */
    private static final int MAX_CONFIRM_ATTEMPTS = 10;

    private final StockService stockService;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final Clock clock;
    private final OrderEventPublisher orderEventPublisher;
    private final QueuedReservationService queuedReservationService;
    private final MeterRegistry meterRegistry;
    private final OrderService self;

    public OrderService(
            StockService stockService,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            OrderRepository orderRepository,
            OrderEventRepository orderEventRepository,
            Clock clock,
            OrderEventPublisher orderEventPublisher,
            QueuedReservationService queuedReservationService,
            MeterRegistry meterRegistry,
            @Lazy OrderService self) {
        this.stockService = stockService;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.clock = clock;
        this.orderEventPublisher = orderEventPublisher;
        this.queuedReservationService = queuedReservationService;
        this.meterRegistry = meterRegistry;
        this.self = self;
    }

    /**
     * Default reservation path — pessimistic locking (§6.2, §14). Benchmarked in §13 against
     * the optimistic-locking alternative below: pessimistic had zero write-conflict failures
     * for already-Redis-approved requests (vs. 8/2000 under optimistic) at a modest latency
     * cost, which wins for a flash-sale reservation write where every Redis-approved request
     * should succeed.
     */
    public Order reserve(UUID customerId, String sku, int qty) {
        Product product = productRepository.findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));

        StockReservationResult result = stockService.tryReserve(sku, qty);
        if (!result.reserved()) {
            meterRegistry.counter("rushcart.reservation.outcome", "result", "insufficient_stock").increment();
            throw new InsufficientStockException(sku);
        }

        try {
            Order order = self.confirmReservationPessimistic(customerId, product.getId(), sku, qty);
            meterRegistry.counter("rushcart.reservation.outcome", "result", "success").increment();
            return order;
        } catch (ReservationQueuedException e) {
            // not a failure — the Redis-approved reservation is durably queued (§8.2),
            // so the Redis stock decrement must NOT be compensated
            meterRegistry.counter("rushcart.reservation.outcome", "result", "queued").increment();
            throw e;
        } catch (RuntimeException e) {
            meterRegistry.counter("rushcart.reservation.outcome", "result", "failure").increment();
            stockService.compensate(sku, qty);
            throw e;
        }
    }

    /**
     * §6.2 optimistic-locking alternative, kept for the §13 load-test comparison — not on
     * the default reservation path. See {@link #reserve} for the chosen strategy.
     */
    public Order reserveOptimistic(UUID customerId, String sku, int qty) {
        Product product = productRepository.findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));

        StockReservationResult result = stockService.tryReserve(sku, qty);
        if (!result.reserved()) {
            throw new InsufficientStockException(sku);
        }

        int attempts = 0;
        while (true) {
            try {
                return self.confirmReservation(customerId, product.getId(), sku, qty);
            } catch (ObjectOptimisticLockingFailureException e) {
                attempts++;
                if (attempts >= MAX_CONFIRM_ATTEMPTS) {
                    stockService.compensate(sku, qty);
                    throw new ReservationConflictException(sku);
                }
            } catch (RuntimeException e) {
                stockService.compensate(sku, qty);
                throw e;
            }
        }
    }

    @Transactional
    public Order confirmReservation(UUID customerId, UUID productId, String sku, int qty) {
        Inventory inventory = inventoryRepository.findById(productId).orElseThrow();
        inventory.reserve(qty);
        inventoryRepository.save(inventory);

        Instant now = clock.instant();
        Order order = new Order(customerId, productId, qty, now);
        order.transitionTo(OrderStatus.RESERVED, now);
        order.setReservationExpiresAt(now.plus(RESERVATION_WINDOW));
        Order saved = orderRepository.save(order);
        orderEventRepository.save(new OrderEvent(saved.getId(), "RESERVED", now));
        orderEventPublisher.publishOrderCreatedAfterCommit(
                new OrderCreatedEvent(saved.getId(), customerId, sku, qty));
        return saved;
    }

    /**
     * §8.2 graceful degradation: when the Postgres write path's circuit breaker is open
     * (connection-pool saturation / write errors), {@link #confirmReservationFallback}
     * queues the already-Redis-approved request instead of failing it.
     */
    @CircuitBreaker(name = "reservationWrite", fallbackMethod = "confirmReservationFallback")
    @Transactional
    public Order confirmReservationPessimistic(UUID customerId, UUID productId, String sku, int qty) {
        Inventory inventory = inventoryRepository.lockForUpdate(productId).orElseThrow();
        inventory.reserve(qty);
        inventoryRepository.save(inventory);

        Instant now = clock.instant();
        Order order = new Order(customerId, productId, qty, now);
        order.transitionTo(OrderStatus.RESERVED, now);
        order.setReservationExpiresAt(now.plus(RESERVATION_WINDOW));
        Order saved = orderRepository.save(order);
        orderEventRepository.save(new OrderEvent(saved.getId(), "RESERVED", now));
        orderEventPublisher.publishOrderCreatedAfterCommit(
                new OrderCreatedEvent(saved.getId(), customerId, sku, qty));
        return saved;
    }

    public Order confirmReservationFallback(UUID customerId, UUID productId, String sku, int qty, Throwable t) {
        queuedReservationService.enqueue(customerId, productId, sku, qty);
        throw new ReservationQueuedException(sku);
    }

    /**
     * Retries a previously-queued reservation (§8.2 drain path). Redis stock was already
     * decremented when the request was first queued, so this goes straight to the DB write —
     * still through the circuit breaker, so a still-open breaker re-queues it via
     * {@link #confirmReservationFallback} rather than losing it.
     */
    public Order confirmQueuedReservation(QueuedReservationRequest request) {
        return self.confirmReservationPessimistic(
                request.customerId(), request.productId(), request.sku(), request.qty());
    }

    public Order get(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /** Dashboard order list (§14.1) — newest first, optionally filtered by status/customer. */
    public java.util.List<Order> list(OrderStatus status, UUID customerId, int limit) {
        return orderRepository.search(
                status, customerId, org.springframework.data.domain.PageRequest.of(0, Math.min(Math.max(limit, 1), 500)));
    }

    /** Full append-only transition timeline for an order (§6.1), newest first. */
    public java.util.List<OrderEvent> events(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException(orderId);
        }
        return orderEventRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    @Transactional
    public Order markPaid(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        Instant now = clock.instant();
        order.transitionTo(OrderStatus.PAID, now);
        orderEventRepository.save(new OrderEvent(order.getId(), "PAID", now));
        return order;
    }

    @Transactional
    public Order cancel(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        Instant now = clock.instant();
        order.transitionTo(OrderStatus.CANCELLED, now);
        orderEventRepository.save(new OrderEvent(order.getId(), "CANCELLED", now));

        Product product = productRepository.findById(order.getProductId())
                .orElseThrow(() -> new IllegalStateException("Product missing for order " + orderId));
        stockService.compensate(product.getSku(), order.getQty());

        return order;
    }
}
