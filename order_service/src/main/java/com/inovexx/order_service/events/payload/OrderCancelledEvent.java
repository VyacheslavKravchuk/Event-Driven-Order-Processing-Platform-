package com.inovexx.order_service.events.payload;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record OrderCancelledEvent(
        Long orderId,
        Long userId,
        String email,
        BigDecimal totalAmount,
        OffsetDateTime orderDate,
        String status,
        String reason
) {
}
