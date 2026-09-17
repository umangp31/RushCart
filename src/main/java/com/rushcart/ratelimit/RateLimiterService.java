package com.rushcart.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis-backed Token Bucket rate limiter (§7). Atomic via a Lua script for the same
 * reason as the stock-decrement script (§5.1): no external lock needed, single round trip.
 */
@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public RateLimiterService(StringRedisTemplate redisTemplate, Clock clock, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("ratelimit/lua/rate_limit.lua"));
        script.setResultType(Long.class);
        this.rateLimitScript = script;
    }

    /** Returns true if the request is allowed, false if the client's bucket is exhausted. */
    public boolean tryAcquire(String clientKey, int capacity, double refillPerSecond) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Long result = redisTemplate.execute(
                rateLimitScript,
                List.of("ratelimit:" + clientKey),
                String.valueOf(capacity),
                String.valueOf(refillPerSecond),
                String.valueOf(clock.instant().toEpochMilli()),
                "1");
        sample.stop(meterRegistry.timer("rushcart.ratelimit.script.latency"));
        return result != null && result == 1L;
    }
}
