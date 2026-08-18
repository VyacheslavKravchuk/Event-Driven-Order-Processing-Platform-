package com.inovexx.user_service.exception;

import lombok.Data;
import java.util.Map;

@Data
public class ErrorResponse {

    private int statusCode;
    private String message;

    private Map<String, String> errors;

    public ErrorResponse(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public ErrorResponse(int statusCode, String message, Map<String, String> errors) {
        this.statusCode = statusCode;
        this.message = message;
        this.errors = errors;
    }
}
