package com.inovexx.order_service.dto;

import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for {@link Order}
 */
public record OrderDto(
        UUID orderId,
        Long userId,
        String customerEmail,
        BigDecimal totalAmount,
        OffsetDateTime orderDate,
        OrderStatus status,
        List<OrderItemDto> orderItems
) {}