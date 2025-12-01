package com.inovexx.user_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class UserAlreadyExistsException extends ResponseStatusException {

    public UserAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, message); // HTTP 409 Conflict
    }
}
