package com.inovexx.user_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Класс для обработки ошибок поиска кошелька
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class WalletRegisteredNotFoundException extends RuntimeException{

    public WalletRegisteredNotFoundException(String message) {
        super(message);
    }
}
