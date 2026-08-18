package com.inovexx.user_service.exception;

/**
 * Класс для обработки ошибок не корректных параметров
 */
public class IllegalArgumentWalletException extends RuntimeException{

    public IllegalArgumentWalletException(String message) {
        super(message);
    }
}
