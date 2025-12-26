package com.inovexx.user_service.exception;

public class WalletRequestNotFoundException extends RuntimeException{

    public WalletRequestNotFoundException(String message) {
        super(message);
    }
}
