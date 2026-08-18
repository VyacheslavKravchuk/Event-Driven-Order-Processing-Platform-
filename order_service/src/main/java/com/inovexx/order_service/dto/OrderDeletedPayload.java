package com.inovexx.order_service.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderDeletedPayload(
        UUID orderId,
        String reason,
        OffsetDateTime deletedAt
) {}
