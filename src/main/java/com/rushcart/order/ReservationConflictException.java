package com.rushcart.order;

import com.rushcart.common.ApiException;
import org.springframework.http.HttpStatus;

public class ReservationConflictException extends ApiException {

    public ReservationConflictException(String sku) {
        super("Could not confirm reservation for SKU " + sku + " after repeated write conflicts");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
