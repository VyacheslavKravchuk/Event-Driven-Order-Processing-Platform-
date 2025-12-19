package com.inovexx.notification_service.events;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCompletedEvent(
        Long orderId,
        String userEmail,
        String username,
        BigDecimal totalAmount,
        Instant timestamp,
        String eventType
) {}
