package com.rushcart.order;

import java.time.Instant;
import java.util.UUID;

public record QueuedReservationRequest(
        UUID id, UUID customerId, UUID productId, String sku, int qty, Instant queuedAt) {}
