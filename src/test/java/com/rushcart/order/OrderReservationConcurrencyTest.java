package com.rushcart.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.inventory.Inventory;
import com.rushcart.inventory.InventoryRepository;
import com.rushcart.inventory.Product;
import com.rushcart.inventory.ProductRepository;
import com.rushcart.inventory.StockService;
import com.rushcart.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OrderReservationConcurrencyTest extends AbstractIntegrationTest {

    private static final String SKU = "SKU-FLASH-SALE";
    private static final int INITIAL_STOCK = 50;
    private static final int CONCURRENT_REQUESTS = 200;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void concurrentReservationRequestsNeverOversellThroughHttpLayer() throws InterruptedException {
        Product product = productRepository.save(new Product(SKU, "Flash Sale Item", new BigDecimal("49.99")));
        inventoryRepository.save(new Inventory(product.getId(), INITIAL_STOCK));
        stockService.seed(SKU, INITIAL_STOCK);

        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger unexpectedCount = new AtomicInteger();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    var request = new OrderController.ReserveOrderRequest(UUID.randomUUID(), SKU, 1);
                    // distinct client per request — this test is about oversell prevention,
                    // not rate limiting, so each simulated customer gets its own budget.
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-Api-Key", UUID.randomUUID().toString());
                    ResponseEntity<String> response = restTemplate.postForEntity(
                            "/api/v1/orders", new HttpEntity<>(request, headers), String.class);
                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    unexpectedCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(unexpectedCount.get()).as("unexpected responses/errors").isZero();
        assertThat(successCount.get()).isEqualTo(INITIAL_STOCK);
        assertThat(conflictCount.get()).isEqualTo(CONCURRENT_REQUESTS - INITIAL_STOCK);
        assertThat(orderRepositoryCount()).isEqualTo(INITIAL_STOCK);
    }

    @Autowired
    private OrderRepository orderRepository;

    private long orderRepositoryCount() {
        return orderRepository.count();
    }
}
