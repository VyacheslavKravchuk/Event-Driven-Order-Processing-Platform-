package com.inovexx.order_service.dto;

import java.math.BigDecimal;

public record ProductEvent(

        String productId,
        String type,
        BigDecimal price,
        String name
) {
}
