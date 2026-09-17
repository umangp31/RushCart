package com.rushcart.order;

import java.util.UUID;

public record OrderCreatedEvent(UUID orderId, UUID customerId, String sku, int qty) {}
