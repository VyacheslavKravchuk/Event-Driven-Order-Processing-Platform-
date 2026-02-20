package com.inovexx.order_service.dto;

import com.inovexx.order_service.entity.Order;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for {@link Order}
 */
public record OrderDto(
        @NotNull Long userId,
        @NotNull List<OrderItemDto> orderItems,
        BigDecimal totalAmount
) {}