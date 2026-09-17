package com.rushcart.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.inventory.Inventory;
import com.rushcart.inventory.InventoryRepository;
import com.rushcart.inventory.Product;
import com.rushcart.inventory.ProductRepository;
import com.rushcart.inventory.StockService;
import com.rushcart.order.Order;
import com.rushcart.order.OrderService;
import com.rushcart.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class IdempotencyServiceTest extends AbstractIntegrationTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderService orderService;

    private UUID persistOrder(String sku) {
        Product product = productRepository.save(new Product(sku, "Idempotency Test Item", new BigDecimal("5.00")));
        inventoryRepository.save(new Inventory(product.getId(), 10));
        stockService.seed(sku, 10);
        Order order = orderService.reserve(UUID.randomUUID(), sku, 1);
        return order.getId();
    }

    @Test
    void firstSeenKeyIsNotADuplicate() {
        String key = UUID.randomUUID().toString();
        assertThat(idempotencyService.isDuplicate(key)).isFalse();
    }

    @Test
    void repeatedKeyWithinRedisTtlIsADuplicate() {
        String key = UUID.randomUUID().toString();
        UUID orderId = persistOrder("SKU-IDEMP-1");
        idempotencyService.isDuplicate(key);
        idempotencyService.markProcessed(key, orderId);

        assertThat(idempotencyService.isDuplicate(key)).isTrue();
    }

    @Test
    void dedupeHoldsViaPostgresBackstopAfterRedisKeyEvicted() {
        String key = UUID.randomUUID().toString();
        UUID orderId = persistOrder("SKU-IDEMP-2");
        idempotencyService.isDuplicate(key);
        idempotencyService.markProcessed(key, orderId);

        // simulate the Redis key expiring before a redelivered message arrives
        redisTemplate.delete("idempotency:" + key);

        assertThat(idempotencyService.isDuplicate(key)).isTrue();
    }
}
