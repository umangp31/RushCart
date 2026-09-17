package com.rushcart.payment;

import com.rushcart.common.IdempotencyService;
import com.rushcart.config.KafkaConfig;
import com.rushcart.order.OrderCreatedEvent;
import com.rushcart.order.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Idempotent by construction (§8.1): the order id is used as the dedupe key, so
 * duplicate delivery — retries, consumer-group rebalances — never double-fulfills.
 */
@Component
public class FulfillmentConsumer {

    private final IdempotencyService idempotencyService;
    private final PaymentService paymentService;
    private final OrderService orderService;

    public FulfillmentConsumer(
            IdempotencyService idempotencyService, PaymentService paymentService, OrderService orderService) {
        this.idempotencyService = idempotencyService;
        this.paymentService = paymentService;
        this.orderService = orderService;
    }

    @KafkaListener(
            topics = KafkaConfig.ORDER_CREATED_TOPIC,
            containerFactory = "orderCreatedKafkaListenerContainerFactory",
            autoStartup = "${rushcart.fulfillment.enabled:true}")
    public void onOrderCreated(OrderCreatedEvent event) {
        String idempotencyKey = event.orderId().toString();
        if (idempotencyService.isDuplicate(idempotencyKey)) {
            return;
        }
        paymentService.confirm(event.orderId());
        orderService.markPaid(event.orderId());
        idempotencyService.markProcessed(idempotencyKey, event.orderId());
    }
}
