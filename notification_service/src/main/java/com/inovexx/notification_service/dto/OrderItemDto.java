package com.inovexx.notification_service.dto;

public record OrderItemDto(
        String productName,
        Integer quantity,
        Double price
) {}
