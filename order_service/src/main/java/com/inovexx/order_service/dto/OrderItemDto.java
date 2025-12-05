package com.inovexx.order_service.dto;

import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.entity.OrderItem;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for {@link OrderItem}
 */
public record OrderItemDto(@NotNull Order order,
                           @NotNull Long productId,
                           @NotNull Integer quantity,
                           @NotNull Double price) {
}