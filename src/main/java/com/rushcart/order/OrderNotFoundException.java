package com.rushcart.order;

import com.rushcart.common.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class OrderNotFoundException extends ApiException {

    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
