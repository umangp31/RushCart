package com.rushcart.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class ReplenishmentServiceConcurrencyTest extends AbstractIntegrationTest {

    private static final String SKU = "SKU-REPLENISH";
    private static final int CONCURRENT_REPLENISH_CALLS = 20;
    private static final int QTY_PER_CALL = 5;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReplenishmentService replenishmentService;

    @Autowired
    private StockService stockService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void concurrentReplenishCallsLeaveBothStoresConsistent() throws InterruptedException {
        Product product = productRepository.save(new Product(SKU, "Restock Item", new BigDecimal("9.99")));
        inventoryRepository.save(new Inventory(product.getId(), 0));
        stockService.seed(SKU, 0);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REPLENISH_CALLS);

        for (int i = 0; i < CONCURRENT_REPLENISH_CALLS; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    replenishmentService.replenish(SKU, QTY_PER_CALL);
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

        int expectedTotal = CONCURRENT_REPLENISH_CALLS * QTY_PER_CALL;

        Inventory inventory = inventoryRepository.findById(product.getId()).orElseThrow();
        assertThat(inventory.getAvailableQty()).isEqualTo(expectedTotal);

        String redisStock = redisTemplate.opsForValue().get("stock:" + SKU);
        assertThat(redisStock).isEqualTo(String.valueOf(expectedTotal));
    }
}
