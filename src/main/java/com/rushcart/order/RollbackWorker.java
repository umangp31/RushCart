package com.rushcart.order;

import com.rushcart.inventory.Product;
import com.rushcart.inventory.ProductRepository;
import com.rushcart.inventory.StockService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sweeps timed-out reservations (§6.3). Safe to run on multiple instances: the claim
 * update in {@link OrderRepository#claimExpired} only affects a row for whichever
 * instance gets there first.
 *
 * <p>The {@code rushcart.rollback.enabled} flag lets tests disable the periodic trigger
 * (which would otherwise deadlock with {@code TRUNCATE}-based test fixture resets) while
 * still calling {@link #sweepExpiredReservations} directly.
 */
@Component
public class RollbackWorker {

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final Clock clock;
    private final RollbackWorker self;
    private final boolean scheduledSweepEnabled;

    public RollbackWorker(
            OrderRepository orderRepository,
            OrderEventRepository orderEventRepository,
            ProductRepository productRepository,
            StockService stockService,
            Clock clock,
            @Lazy RollbackWorker self,
            @Value("${rushcart.rollback.enabled:true}") boolean scheduledSweepEnabled) {
        this.orderRepository = orderRepository;
        this.orderEventRepository = orderEventRepository;
        this.productRepository = productRepository;
        this.stockService = stockService;
        this.clock = clock;
        this.self = self;
        this.scheduledSweepEnabled = scheduledSweepEnabled;
    }

    @Scheduled(fixedDelayString = "${rushcart.rollback.fixed-delay-ms:5000}")
    void scheduledSweep() {
        if (scheduledSweepEnabled) {
            sweepExpiredReservations();
        }
    }

    public void sweepExpiredReservations() {
        Instant now = clock.instant();
        for (var orderId : orderRepository.findExpiredReservationIds(now)) {
            self.claimAndExpire(orderId, now);
        }
    }

    @Transactional
    public void claimAndExpire(java.util.UUID orderId, Instant now) {
        if (orderRepository.claimExpired(orderId, now) != 1) {
            return;
        }
        Order order = orderRepository.findById(orderId).orElseThrow();
        Product product = productRepository.findById(order.getProductId()).orElseThrow();
        stockService.compensate(product.getSku(), order.getQty());
        orderEventRepository.save(new OrderEvent(orderId, "EXPIRED", now));
    }
}
