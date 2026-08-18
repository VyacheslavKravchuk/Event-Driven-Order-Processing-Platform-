package com.inovexx.inventory_service.dto;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record ReservationLogDto(

        Long orderId,

        String productId,

        String status,

        int quantity,

        OffsetDateTime timestamp

) {
}
