package com.inovexx.order_service.exception;

public class CompensationFailedException extends RuntimeException {
    public CompensationFailedException(String message) {
        super(message);
    }
}
