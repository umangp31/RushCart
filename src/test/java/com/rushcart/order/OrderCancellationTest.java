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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class OrderCancellationTest extends AbstractIntegrationTest {

    private static final String SKU = "SKU-CANCEL";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void cancellingReservedOrderReleasesStockBackToRedis() {
        Product product = productRepository.save(new Product(SKU, "Cancel Me", new BigDecimal("19.99")));
        inventoryRepository.save(new Inventory(product.getId(), 10));
        stockService.seed(SKU, 10);

        Order order = orderService.reserve(UUID.randomUUID(), SKU, 3);
        assertThat(redisTemplate.opsForValue().get("stock:" + SKU)).isEqualTo("7");

        Order cancelled = orderService.cancel(order.getId());

        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(redisTemplate.opsForValue().get("stock:" + SKU)).isEqualTo("10");
    }
}
