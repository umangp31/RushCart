package com.rushcart.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.rushcart.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class StockServiceCompensationTest extends AbstractIntegrationTest {

    private static final String SKU = "SKU-COMPENSATE";

    @Autowired
    private StockService stockService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void compensateRestoresStockAfterFailedReservation() {
        stockService.seed(SKU, 10);

        StockReservationResult reserved = stockService.tryReserve(SKU, 3);
        assertThat(reserved.reserved()).isTrue();
        assertThat(reserved.remainingStock()).isEqualTo(7);

        stockService.compensate(SKU, 3);

        String remaining = redisTemplate.opsForValue().get("stock:" + SKU);
        assertThat(remaining).isEqualTo("10");
    }
}
