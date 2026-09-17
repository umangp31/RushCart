package com.rushcart.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderStateMachineTest {

    private static final Set<String> LEGAL_EDGES = Set.of(
            "PENDING->RESERVED",
            "PENDING->CANCELLED",
            "RESERVED->PAID",
            "RESERVED->EXPIRED",
            "RESERVED->CANCELLED");

    @Test
    void onlyGraphLegalTransitionsAreAccepted() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                boolean expectedLegal = LEGAL_EDGES.contains(from + "->" + to);
                assertThat(OrderStateMachine.isValidTransition(from, to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(expectedLegal);
            }
        }
    }

    @Test
    void assertValidTransitionThrowsOnIllegalEdge() {
        assertThatThrownBy(() -> OrderStateMachine.assertValidTransition(OrderStatus.PAID, OrderStatus.RESERVED))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void assertValidTransitionPassesOnLegalEdge() {
        OrderStateMachine.assertValidTransition(OrderStatus.PENDING, OrderStatus.RESERVED);
    }
}
