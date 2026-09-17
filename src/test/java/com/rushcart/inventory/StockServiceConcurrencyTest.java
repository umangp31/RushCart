package com.rushcart.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.support.AbstractIntegrationTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class StockServiceConcurrencyTest extends AbstractIntegrationTest {

    private static final String SKU = "SKU-SCARCE";
    private static final int INITIAL_STOCK = 50;
    private static final int CONCURRENT_REQUESTS = 200;

    @Autowired
    private StockService stockService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void neverOversellsUnderConcurrentReservationAttempts() throws InterruptedException {
        stockService.seed(SKU, INITIAL_STOCK);

        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    StockReservationResult result = stockService.tryReserve(SKU, 1);
                    if (result.reserved()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
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

        assertThat(successCount.get()).isEqualTo(INITIAL_STOCK);
        assertThat(failureCount.get()).isEqualTo(CONCURRENT_REQUESTS - INITIAL_STOCK);

        String remaining = redisTemplate.opsForValue().get("stock:" + SKU);
        assertThat(remaining).isEqualTo("0");
    }
}
