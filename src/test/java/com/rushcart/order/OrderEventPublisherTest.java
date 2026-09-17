package com.rushcart.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.inventory.Inventory;
import com.rushcart.inventory.InventoryRepository;
import com.rushcart.inventory.Product;
import com.rushcart.inventory.ProductRepository;
import com.rushcart.inventory.StockService;
import com.rushcart.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderEventPublisherTest extends AbstractIntegrationTest {

    private static final String SKU = "SKU-EVENT-TEST";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderService orderService;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void subscribeToTopic() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("order-created"));
    }

    @AfterEach
    void closeConsumer() {
        consumer.close();
    }

    @Test
    void publishesExactlyOneEventPerSuccessfulReservationAndNoneOnFailure() {
        Product product = productRepository.save(new Product(SKU, "Event Test Item", new BigDecimal("29.99")));
        inventoryRepository.save(new Inventory(product.getId(), 1));
        stockService.seed(SKU, 1);

        orderService.reserve(UUID.randomUUID(), SKU, 1);

        try {
            orderService.reserve(UUID.randomUUID(), SKU, 1);
        } catch (InsufficientStockException ignored) {
            // expected: stock is exhausted, no event should be published for this attempt
        }

        List<ConsumerRecord<String, String>> records = pollAll();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).value()).contains(SKU);
    }

    private List<ConsumerRecord<String, String>> pollAll() {
        List<ConsumerRecord<String, String>> all = new java.util.ArrayList<>();
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            records.forEach(all::add);
            if (!all.isEmpty()) {
                // drain any remaining records from this poll cycle, then stop
                ConsumerRecords<String, String> more = consumer.poll(Duration.ofMillis(200));
                more.forEach(all::add);
                break;
            }
        }
        return all;
    }
}
