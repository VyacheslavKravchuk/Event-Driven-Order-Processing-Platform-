package com.inovexx.notification_service.dto.events;

import com.inovexx.notification_service.dto.OrderItemDto;

import java.util.List;

public record OrderNotificationEvent(
        String orderId,
        String customerEmail,
        String customerName,
        List<OrderItemDto> items, // Список товаров
        Double totalAmount,
        String status
) {}
