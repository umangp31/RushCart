package com.rushcart.common;

import org.springframework.http.HttpStatus;

/**
 * Base for exceptions that should be translated into an RFC 7807 problem response (§9, §25).
 */
public abstract class ApiException extends RuntimeException {

    protected ApiException(String message) {
        super(message);
    }

    public abstract HttpStatus status();
}
