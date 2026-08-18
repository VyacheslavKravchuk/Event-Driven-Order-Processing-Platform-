package com.inovexx.order_service.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Record для отправки события в Kafka.
 */
public record OrderNotificationEvent(
        UUID orderId,
        Long userId,
        String customerEmail,
        String status,
        BigDecimal totalAmount,
        List<OrderItemRecord> items
) {
    /**
     * Вложенный record для представления позиций заказа.
     */
    public record OrderItemRecord(
            String productId,
            Integer quantity,
            BigDecimal price
    ) {}
}
