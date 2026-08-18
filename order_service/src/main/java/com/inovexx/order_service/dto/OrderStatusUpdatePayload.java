package com.inovexx.order_service.dto;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
public record OrderStatusUpdatePayload(
        UUID orderId,
        String status,
        String customerEmail,
        OffsetDateTime updatedAt
) {}