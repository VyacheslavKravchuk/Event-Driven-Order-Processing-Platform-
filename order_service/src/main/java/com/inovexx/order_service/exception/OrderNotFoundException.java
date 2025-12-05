package com.inovexx.order_service.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super("Запись о продукте с id: " + id + "не найдена");
    }
}
