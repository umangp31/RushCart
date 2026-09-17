package com.rushcart.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drains {@link QueuedReservationService} once the reservation-write circuit breaker
 * has recovered (§8.2). {@code rushcart.drain.enabled} lets tests disable the periodic
 * trigger while still calling {@link #drainQueue} directly.
 */
@Component
public class QueuedReservationDrainWorker {

    private final QueuedReservationService queuedReservationService;
    private final OrderService orderService;
    private final boolean scheduledDrainEnabled;

    public QueuedReservationDrainWorker(
            QueuedReservationService queuedReservationService,
            OrderService orderService,
            @Value("${rushcart.drain.enabled:true}") boolean scheduledDrainEnabled) {
        this.queuedReservationService = queuedReservationService;
        this.orderService = orderService;
        this.scheduledDrainEnabled = scheduledDrainEnabled;
    }

    @Scheduled(fixedDelayString = "${rushcart.drain.fixed-delay-ms:3000}")
    void scheduledDrain() {
        if (scheduledDrainEnabled) {
            drainQueue();
        }
    }

    public void drainQueue() {
        while (true) {
            QueuedReservationRequest request = queuedReservationService.drainOne();
            if (request == null) {
                return;
            }
            try {
                orderService.confirmQueuedReservation(request);
            } catch (ReservationQueuedException e) {
                // breaker still open — confirmReservationFallback already re-enqueued this
                // request, so stop this cycle rather than busy-looping on it.
                return;
            } catch (RuntimeException e) {
                // genuine unexpected failure: put it back rather than losing it, then stop.
                queuedReservationService.requeueFront(request);
                return;
            }
        }
    }
}
