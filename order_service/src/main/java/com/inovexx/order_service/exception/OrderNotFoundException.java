package com.inovexx.order_service.exception;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID id) {
        super("Запись о продукте с id: " + id + "не найдена");
    }
}
