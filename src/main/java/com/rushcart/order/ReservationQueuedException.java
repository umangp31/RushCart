package com.rushcart.order;

import com.rushcart.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Not a failure (§8.2): the Redis-approved reservation was accepted into the queued-
 * reservation buffer because the Postgres write path's circuit breaker is open. Maps to
 * {@code 202 Accepted}, never a 4xx/5xx.
 */
public class ReservationQueuedException extends ApiException {

    public ReservationQueuedException(String sku) {
        super("Reservation for SKU " + sku + " accepted and queued for confirmation");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.ACCEPTED;
    }
}
