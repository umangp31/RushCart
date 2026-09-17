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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class CircuitBreakerFallbackTest extends AbstractIntegrationTest {

    private static final String SKU = "SKU-BREAKER-TEST";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private QueuedReservationService queuedReservationService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private org.springframework.boot.test.web.client.TestRestTemplate restTemplate;

    @AfterEach
    void resetBreaker() {
        circuitBreakerRegistry.circuitBreaker("reservationWrite").transitionToClosedState();
    }

    @Test
    void openCircuitQueuesReservationInsteadOfFailingTheCaller() {
        Product product = productRepository.save(new Product(SKU, "Breaker Test Item", new BigDecimal("15.00")));
        inventoryRepository.save(new Inventory(product.getId(), 10));
        stockService.seed(SKU, 10);

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("reservationWrite");
        breaker.transitionToOpenState();

        var request = new OrderController.ReserveOrderRequest(UUID.randomUUID(), SKU, 1);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/orders", request, String.class);

        // §8.2: a Redis-approved reservation must never surface as an error while the
        // breaker is open — 202 Accepted, not 4xx/5xx.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Redis stock was decremented and must NOT be compensated — the reservation is
        // still "claimed," just queued for later confirmation.
        assertThat(redisTemplate.opsForValue().get("stock:" + SKU)).isEqualTo("9");
        assertThat(queuedReservationService.size()).isEqualTo(1L);
    }
}
