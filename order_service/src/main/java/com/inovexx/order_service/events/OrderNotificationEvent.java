package com.inovexx.order_service.events;

import java.math.BigDecimal;
import java.util.List;
/**
 * Record для отправки события в Kafka.
 */
public record OrderNotificationEvent(
        Long orderId,
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
