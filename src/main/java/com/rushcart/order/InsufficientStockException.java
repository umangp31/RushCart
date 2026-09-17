package com.rushcart.order;

import com.rushcart.common.ApiException;
import org.springframework.http.HttpStatus;

public class InsufficientStockException extends ApiException {

    public InsufficientStockException(String sku) {
        super("Insufficient stock for SKU " + sku);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
