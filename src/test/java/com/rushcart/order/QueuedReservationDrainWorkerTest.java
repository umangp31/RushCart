package com.rushcart.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.inventory.Inventory;
import com.rushcart.inventory.InventoryRepository;
import com.rushcart.inventory.Product;
import com.rushcart.inventory.ProductRepository;
import com.rushcart.inventory.StockService;
import com.rushcart.support.AbstractIntegrationTest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QueuedReservationDrainWorkerTest extends AbstractIntegrationTest {

    private static final String SKU = "SKU-DRAIN-TEST";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private QueuedReservationService queuedReservationService;

    @Autowired
    private QueuedReservationDrainWorker drainWorker;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @AfterEach
    void resetBreaker() {
        circuitBreakerRegistry.circuitBreaker("reservationWrite").transitionToClosedState();
    }

    @Test
    void queuedDuringOutageIsDrainedSuccessfullyAfterRecoveryWithNoDataLoss() {
        Product product = productRepository.save(new Product(SKU, "Drain Test Item", new BigDecimal("22.00")));
        inventoryRepository.save(new Inventory(product.getId(), 5));
        stockService.seed(SKU, 5);

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("reservationWrite");
        breaker.transitionToOpenState();

        UUID customerId = UUID.randomUUID();
        try {
            orderService.reserve(customerId, SKU, 1);
        } catch (ReservationQueuedException expected) {
            // expected: breaker open, request queued instead of failing
        }
        assertThat(queuedReservationService.size()).isEqualTo(1L);
        assertThat(orderRepository.count()).isZero();

        // simulate recovery
        breaker.transitionToClosedState();
        drainWorker.drainQueue();

        assertThat(queuedReservationService.size()).isZero();
        assertThat(orderRepository.count()).isEqualTo(1);
        Order drained = orderRepository.findAll().get(0);
        assertThat(drained.getCustomerId()).isEqualTo(customerId);
        assertThat(drained.getStatus()).isEqualTo(OrderStatus.RESERVED);
    }
}
