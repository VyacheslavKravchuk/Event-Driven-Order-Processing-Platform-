package com.inovexx.order_service.dto;

import java.time.OffsetDateTime;

public record OrderDeletedPayload(
        Long orderId,
        String reason,
        OffsetDateTime deletedAt
) {}
