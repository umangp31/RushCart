package com.rushcart.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.inventory.Inventory;
import com.rushcart.inventory.InventoryRepository;
import com.rushcart.inventory.Product;
import com.rushcart.inventory.ProductRepository;
import com.rushcart.inventory.StockService;
import com.rushcart.order.OrderService;
import com.rushcart.ratelimit.RateLimiterService;
import com.rushcart.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;

@AutoConfigureObservability
class MetricsTest extends AbstractIntegrationTest {

    private static final String SKU = "SKU-METRICS-TEST";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void prometheusScrapeShowsAllCustomMetricFamilies() {
        Product product = productRepository.save(new Product(SKU, "Metrics Test Item", new BigDecimal("9.99")));
        inventoryRepository.save(new Inventory(product.getId(), 5));
        stockService.seed(SKU, 5);
        orderService.reserve(UUID.randomUUID(), SKU, 1);
        rateLimiterService.tryAcquire("metrics-test-client-" + UUID.randomUUID(), 10, 1.0);

        String body = restTemplate.getForObject("/actuator/prometheus", String.class);

        assertThat(body).contains("rushcart_stock_decrement_latency");
        assertThat(body).contains("rushcart_ratelimit_script_latency");
        assertThat(body).contains("rushcart_reservation_outcome");
        assertThat(body).contains("resilience4j_circuitbreaker");
    }
}
