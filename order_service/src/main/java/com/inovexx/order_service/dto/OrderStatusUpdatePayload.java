package com.inovexx.order_service.dto;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record OrderStatusUpdatePayload(
        Long orderId,
        String status,
        String customerEmail,
        OffsetDateTime updatedAt
) {}