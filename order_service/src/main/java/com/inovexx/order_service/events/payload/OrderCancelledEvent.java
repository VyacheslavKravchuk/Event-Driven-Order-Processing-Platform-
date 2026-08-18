package com.inovexx.order_service.events.payload;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID orderId,
        Long userId,
        String email,
        BigDecimal totalAmount,
        OffsetDateTime orderDate,
        String status,
        String reason
) {
}
