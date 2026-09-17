package com.rushcart.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.inventory.Inventory;
import com.rushcart.inventory.InventoryRepository;
import com.rushcart.inventory.Product;
import com.rushcart.inventory.ProductRepository;
import com.rushcart.inventory.StockService;
import com.rushcart.order.Order;
import com.rushcart.order.OrderCreatedEvent;
import com.rushcart.order.OrderRepository;
import com.rushcart.order.OrderService;
import com.rushcart.order.OrderStatus;
import com.rushcart.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FulfillmentConsumerTest extends AbstractIntegrationTest {

    private static final String SKU = "SKU-FULFILL";

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
    private FulfillmentConsumer fulfillmentConsumer;

    @Test
    void duplicateDeliveryResultsInExactlyOneFulfillmentSideEffect() {
        Product product = productRepository.save(new Product(SKU, "Fulfillment Test Item", new BigDecimal("39.99")));
        inventoryRepository.save(new Inventory(product.getId(), 5));
        stockService.seed(SKU, 5);

        Order order = orderService.reserve(UUID.randomUUID(), SKU, 1);
        OrderCreatedEvent event = new OrderCreatedEvent(order.getId(), order.getCustomerId(), SKU, 1);

        fulfillmentConsumer.onOrderCreated(event);
        Order afterFirst = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(OrderStatus.PAID);

        // duplicate delivery of the same message must not attempt a second PAID transition
        fulfillmentConsumer.onOrderCreated(event);
        Order afterSecond = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(afterSecond.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(afterSecond.getUpdatedAt()).isEqualTo(afterFirst.getUpdatedAt());
    }
}
