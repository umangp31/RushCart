package com.rushcart.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.inventory.Inventory;
import com.rushcart.inventory.InventoryRepository;
import com.rushcart.inventory.Product;
import com.rushcart.inventory.ProductRepository;
import com.rushcart.inventory.StockService;
import com.rushcart.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * §10 T1 / §13 final sweep: the full end-to-end (HTTP → rate limiter → Redis → Postgres)
 * scenario at scale, with the production-default pessimistic-locking reservation path.
 */
class ConcurrencyIT extends AbstractIntegrationTest {

    private static final String SKU = "SKU-FINAL-SWEEP";
    private static final int INITIAL_STOCK = 50;
    private static final int CONCURRENT_REQUESTS = 2000;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void fullStackConcurrencySweepAtScale() throws InterruptedException {
        Product product = productRepository.save(new Product(SKU, "Final Sweep Item", new BigDecimal("19.99")));
        inventoryRepository.save(new Inventory(product.getId(), INITIAL_STOCK));
        stockService.seed(SKU, INITIAL_STOCK);

        ExecutorService executor = Executors.newFixedThreadPool(64);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger unexpectedCount = new AtomicInteger();
        List<Long> latenciesMs = new CopyOnWriteArrayList<>();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    var request = new OrderController.ReserveOrderRequest(UUID.randomUUID(), SKU, 1);
                    HttpHeaders headers = new HttpHeaders();
                    // distinct client per request so the §7 rate limiter doesn't confound
                    // this oversell/latency sweep
                    headers.set("X-Api-Key", UUID.randomUUID().toString());
                    long start = System.nanoTime();
                    ResponseEntity<String> response = restTemplate.postForEntity(
                            "/api/v1/orders", new HttpEntity<>(request, headers), String.class);
                    latenciesMs.add((System.nanoTime() - start) / 1_000_000);
                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        doneLatch.await();
        executor.shutdown();

        List<Long> sorted = latenciesMs.stream().sorted().toList();
        long p95 = sorted.get((int) (sorted.size() * 0.95) - 1);
        long p99 = sorted.get((int) (sorted.size() * 0.99) - 1);

        System.out.printf(
                "%n[§13 Final Sweep] requests=%d success=%d conflict=%d unexpected=%d p95=%dms p99=%dms%n",
                CONCURRENT_REQUESTS, successCount.get(), conflictCount.get(), unexpectedCount.get(), p95, p99);

        assertThat(unexpectedCount.get()).isZero();
        assertThat(successCount.get()).isEqualTo(INITIAL_STOCK);
        assertThat(orderRepository.count()).isEqualTo(INITIAL_STOCK);
        assertThat(redisTemplate.opsForValue().get("stock:" + SKU)).isEqualTo("0");
    }
}
