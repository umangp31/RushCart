package com.rushcart.order;

import java.util.Map;
import java.util.Set;

/**
 * Pure transition function for §6.1's order lifecycle graph. Rejects any edge not
 * explicitly listed here — orders.status must never drift outside this graph.
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.RESERVED, OrderStatus.CANCELLED),
            OrderStatus.RESERVED, Set.of(OrderStatus.PAID, OrderStatus.EXPIRED, OrderStatus.CANCELLED),
            OrderStatus.PAID, Set.of(),
            OrderStatus.EXPIRED, Set.of(),
            OrderStatus.CANCELLED, Set.of());

    private OrderStateMachine() {}

    public static boolean isValidTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertValidTransition(OrderStatus from, OrderStatus to) {
        if (!isValidTransition(from, to)) {
            throw new IllegalStateTransitionException(from, to);
        }
    }
}
