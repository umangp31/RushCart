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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OrderPaymentEndpointTest extends AbstractIntegrationTest {

    private static final String SKU = "SKU-PAY-ENDPOINT";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void payEndpointTransitionsReservedOrderToPaidAndIsReflectedOnGet() {
        Product product = productRepository.save(new Product(SKU, "Pay Endpoint Item", new BigDecimal("59.99")));
        inventoryRepository.save(new Inventory(product.getId(), 5));
        stockService.seed(SKU, 5);

        Order order = orderService.reserve(UUID.randomUUID(), SKU, 1);

        ResponseEntity<OrderController.OrderResponse> payResponse = restTemplate.postForEntity(
                "/api/v1/orders/" + order.getId() + "/pay", null, OrderController.OrderResponse.class);
        assertThat(payResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(payResponse.getBody().status()).isEqualTo(OrderStatus.PAID);

        ResponseEntity<OrderController.OrderResponse> getResponse = restTemplate.getForEntity(
                "/api/v1/orders/" + order.getId(), OrderController.OrderResponse.class);
        assertThat(getResponse.getBody().status()).isEqualTo(OrderStatus.PAID);
    }
}
