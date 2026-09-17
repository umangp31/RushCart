package com.rushcart.inventory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis pre-allocation gate (§5.1) — atomic stock check-and-decrement via a Lua script,
 * so the relational DB is never the first line of defense against overselling.
 */
@Service
public class StockService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> decrementScript;
    private final MeterRegistry meterRegistry;

    public StockService(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("inventory/lua/stock_decrement.lua"));
        script.setResultType(Long.class);
        this.decrementScript = script;
    }

    public StockReservationResult tryReserve(String sku, int qty) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Long result = redisTemplate.execute(decrementScript, List.of(stockKey(sku)), String.valueOf(qty));
        sample.stop(meterRegistry.timer("rushcart.stock.decrement.latency"));
        if (result == null || result < 0) {
            return StockReservationResult.insufficientStock();
        }
        return StockReservationResult.reserved(result.intValue());
    }

    /**
     * Reverses a reservation that was approved in Redis but never committed downstream
     * (e.g. the Postgres write failed) — §5.1's compensation path.
     */
    public void compensate(String sku, int qty) {
        redisTemplate.opsForValue().increment(stockKey(sku), qty);
    }

    public void seed(String sku, int qty) {
        redisTemplate.opsForValue().set(stockKey(sku), String.valueOf(qty));
    }

    private String stockKey(String sku) {
        return "stock:" + sku;
    }
}
