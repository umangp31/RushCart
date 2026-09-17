package com.rushcart.order;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEventRepository extends JpaRepository<OrderEvent, UUID> {

    /** Audit-trail read pattern (§4.1 idx_order_events_order_time): latest events for an order. */
    List<OrderEvent> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
