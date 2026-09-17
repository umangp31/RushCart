package com.rushcart.order;

import com.rushcart.common.ApiException;
import org.springframework.http.HttpStatus;

public class IllegalStateTransitionException extends ApiException {

    public IllegalStateTransitionException(OrderStatus from, OrderStatus to) {
        super("Illegal order state transition: %s -> %s".formatted(from, to));
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
