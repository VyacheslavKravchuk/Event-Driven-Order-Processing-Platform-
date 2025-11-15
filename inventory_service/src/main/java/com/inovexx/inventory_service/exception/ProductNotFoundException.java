package com.inovexx.inventory_service.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("Запись о продукте с id: " + id + "не найдена");
    }
}
