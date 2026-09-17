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
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * §13 load test: fires a burst of concurrent reservation attempts against a scarce SKU
 * and records oversell count + latency percentiles for the pessimistic-locking variant
 * ({@link InventoryRepository#lockForUpdate}), to compare against optimistic locking.
 */
class ReservationLoadPessimisticIT extends AbstractIntegrationTest {

    private static final String SKU = "SKU-LOAD-TEST-PESSIMISTIC";
    private static final int INITIAL_STOCK = 50;
    private static final int CONCURRENT_REQUESTS = 2000;

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
    private StringRedisTemplate redisTemplate;

    @Test
    void pessimisticLockingUnderBurstLoad() throws InterruptedException {
        Product product = productRepository.save(new Product(SKU, "Load Test Item", new BigDecimal("9.99")));
        inventoryRepository.save(new Inventory(product.getId(), INITIAL_STOCK));
        stockService.seed(SKU, INITIAL_STOCK);

        ExecutorService executor = Executors.newFixedThreadPool(64);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger insufficientStockCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        List<Long> latenciesMs = new CopyOnWriteArrayList<>();

        Callable<Void> task = () -> {
            startGate.await();
            long start = System.nanoTime();
            try {
                orderService.reserve(UUID.randomUUID(), SKU, 1);
                successCount.incrementAndGet();
            } catch (InsufficientStockException e) {
                insufficientStockCount.incrementAndGet();
            } catch (ReservationConflictException e) {
                conflictCount.incrementAndGet();
            } finally {
                latenciesMs.add((System.nanoTime() - start) / 1_000_000);
                doneLatch.countDown();
            }
            return null;
        };

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executor.submit(task);
        }

        startGate.countDown();
        doneLatch.await();
        executor.shutdown();

        List<Long> sorted = latenciesMs.stream().sorted().toList();
        long p95 = sorted.get((int) (sorted.size() * 0.95) - 1);
        long p99 = sorted.get((int) (sorted.size() * 0.99) - 1);

        System.out.printf(
                "%n[§13 Pessimistic Locking] requests=%d success=%d insufficientStock=%d conflict=%d"
                        + " p95=%dms p99=%dms%n",
                CONCURRENT_REQUESTS, successCount.get(), insufficientStockCount.get(), conflictCount.get(), p95, p99);

        // Oversell assertion: successes must never exceed initial stock, regardless of retries.
        assertThat(successCount.get()).isLessThanOrEqualTo(INITIAL_STOCK);
        assertThat(orderRepository.count()).isEqualTo(successCount.get());
        String remainingStock = redisTemplate.opsForValue().get("stock:" + SKU);
        assertThat(Integer.parseInt(remainingStock)).isEqualTo(INITIAL_STOCK - successCount.get());
    }
}
