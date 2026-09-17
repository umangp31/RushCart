package com.rushcart.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * §8.2 durable buffer for reservations accepted while the circuit breaker guarding the
 * Postgres write path is open. A Redis list acts as the queue; {@link #drainOne} pops
 * and returns the oldest entry so a drain process can retry it once the breaker recovers.
 */
@Service
public class QueuedReservationService {

    private static final String QUEUE_KEY = "queue:reservations";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public QueuedReservationService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public QueuedReservationRequest enqueue(UUID customerId, UUID productId, String sku, int qty) {
        QueuedReservationRequest request =
                new QueuedReservationRequest(UUID.randomUUID(), customerId, productId, sku, qty, clock.instant());
        try {
            redisTemplate.opsForList().rightPush(QUEUE_KEY, objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize queued reservation", e);
        }
        return request;
    }

    /** Puts a request back at the head of the queue so it's retried before newer entries. */
    public void requeueFront(QueuedReservationRequest request) {
        try {
            redisTemplate.opsForList().leftPush(QUEUE_KEY, objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize queued reservation", e);
        }
    }

    public QueuedReservationRequest drainOne() {
        String json = redisTemplate.opsForList().leftPop(QUEUE_KEY);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, QueuedReservationRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize queued reservation", e);
        }
    }

    public Long size() {
        return redisTemplate.opsForList().size(QUEUE_KEY);
    }
}
