package com.rushcart.common;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-tier idempotency check (§8.1): Redis {@code SET NX} is the fast path; the durable
 * {@code idempotency_keys} table is the backstop for when Redis evicts the key before a
 * redelivered message arrives.
 */
@Service
public class IdempotencyService {

    private static final Duration REDIS_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyKeyRepository repository;
    private final Clock clock;

    public IdempotencyService(StringRedisTemplate redisTemplate, IdempotencyKeyRepository repository, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.repository = repository;
        this.clock = clock;
    }

    public boolean isDuplicate(String key) {
        Boolean claimedInRedis = redisTemplate.opsForValue().setIfAbsent(redisKey(key), "1", REDIS_TTL);
        if (Boolean.FALSE.equals(claimedInRedis)) {
            return true;
        }
        return repository.existsById(key);
    }

    @Transactional
    public void markProcessed(String key, UUID orderId) {
        repository.save(new IdempotencyKey(key, orderId, clock.instant()));
    }

    private String redisKey(String key) {
        return "idempotency:" + key;
    }
}
