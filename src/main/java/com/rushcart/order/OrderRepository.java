package com.rushcart.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Dashboard order list (§14.1): newest first, optionally narrowed by status and/or customer. */
    @Query(
            """
            SELECT o FROM Order o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
            ORDER BY o.createdAt DESC
            """)
    List<Order> search(
            @Param("status") OrderStatus status,
            @Param("customerId") UUID customerId,
            org.springframework.data.domain.Pageable pageable);

    @Query(
            value =
                    """
                    SELECT id FROM orders
                    WHERE status = 'RESERVED' AND reservation_expires_at < :now
                    """,
            nativeQuery = true)
    List<UUID> findExpiredReservationIds(@Param("now") Instant now);

    /**
     * Race-safe claim: the status guard means only one worker's UPDATE affects a row
     * even if two instances scan the same expired order concurrently (§6.3).
     */
    @Modifying
    @Query(
            value =
                    """
                    UPDATE orders SET status = 'EXPIRED', updated_at = :now
                    WHERE id = :id AND status = 'RESERVED'
                    """,
            nativeQuery = true)
    int claimExpired(@Param("id") UUID id, @Param("now") Instant now);
}
