package com.rushcart.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.inventory.Inventory;
import com.rushcart.inventory.InventoryRepository;
import com.rushcart.inventory.Product;
import com.rushcart.inventory.ProductRepository;
import com.rushcart.inventory.StockService;
import com.rushcart.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class RollbackWorkerTest extends AbstractIntegrationTest {

    private static final String SKU = "SKU-EXPIRE";

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
    private RollbackWorker rollbackWorker;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void sweepExpiresTimedOutReservationsAndReleasesStock() {
        Product product = productRepository.save(new Product(SKU, "Expiring Item", new BigDecimal("15.00")));
        inventoryRepository.save(new Inventory(product.getId(), 5));
        stockService.seed(SKU, 5);

        Order order = orderService.reserve(UUID.randomUUID(), SKU, 2);
        assertThat(redisTemplate.opsForValue().get("stock:" + SKU)).isEqualTo("3");

        // simulate a reservation that timed out a minute ago
        Order fetched = orderRepository.findById(order.getId()).orElseThrow();
        fetched.setReservationExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        orderRepository.save(fetched);

        rollbackWorker.sweepExpiredReservations();

        Order expired = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(redisTemplate.opsForValue().get("stock:" + SKU)).isEqualTo("5");
    }
}
