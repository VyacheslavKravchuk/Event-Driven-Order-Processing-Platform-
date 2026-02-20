package com.inovexx.order_service.dto;

import com.inovexx.order_service.entity.OrderItem;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO for {@link OrderItem}
 */
public record OrderItemDto(
        @NotNull Long productId,
        @NotNull Integer quantity,
        @NotNull BigDecimal price
) {}