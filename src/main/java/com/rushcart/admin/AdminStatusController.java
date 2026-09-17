package com.rushcart.admin;

import com.rushcart.order.QueuedReservationService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only ops snapshot for the Angular admin dashboard (§14.1): circuit-breaker state,
 * queued-reservation buffer depth (§8.2), and rate-limiter posture (§7).
 */
@RestController
@Tag(name = "admin")
public class AdminStatusController {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final QueuedReservationService queuedReservationService;
    private final StringRedisTemplate redisTemplate;
    private final int rateLimitCapacity;
    private final double rateLimitRefillPerSecond;

    public AdminStatusController(
            CircuitBreakerRegistry circuitBreakerRegistry,
            QueuedReservationService queuedReservationService,
            StringRedisTemplate redisTemplate,
            @Value("${rushcart.ratelimit.capacity:20}") int rateLimitCapacity,
            @Value("${rushcart.ratelimit.refill-per-second:5}") double rateLimitRefillPerSecond) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.queuedReservationService = queuedReservationService;
        this.redisTemplate = redisTemplate;
        this.rateLimitCapacity = rateLimitCapacity;
        this.rateLimitRefillPerSecond = rateLimitRefillPerSecond;
    }

    public record CircuitBreakerStatus(
            String name, String state, float failureRate, long bufferedCalls, long failedCalls) {}

    public record RateLimiterStatus(
            int capacity, double refillPerSecond, boolean redisReachable, boolean failOpen) {}

    public record AdminStatus(
            CircuitBreakerStatus reservationWrite, long queuedReservationDepth, RateLimiterStatus rateLimiter) {}

    @GetMapping("/api/v1/admin/status")
    public AdminStatus status() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("reservationWrite");
        CircuitBreaker.Metrics m = cb.getMetrics();
        CircuitBreakerStatus cbStatus = new CircuitBreakerStatus(
                cb.getName(),
                cb.getState().name(),
                m.getFailureRate(),
                m.getNumberOfBufferedCalls(),
                m.getNumberOfFailedCalls());

        Long depth = queuedReservationService.size();

        boolean redisReachable;
        try {
            redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            redisReachable = true;
        } catch (RuntimeException e) {
            redisReachable = false;
        }

        RateLimiterStatus rl = new RateLimiterStatus(
                rateLimitCapacity, rateLimitRefillPerSecond, redisReachable, !redisReachable);

        return new AdminStatus(cbStatus, depth != null ? depth : 0L, rl);
    }
}
