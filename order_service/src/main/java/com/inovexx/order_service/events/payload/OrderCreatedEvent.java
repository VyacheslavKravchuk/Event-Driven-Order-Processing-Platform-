package com.inovexx.order_service.events.payload;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        String email,
        BigDecimal totalAmount,
        OffsetDateTime orderDate,
        String status
) {
}
