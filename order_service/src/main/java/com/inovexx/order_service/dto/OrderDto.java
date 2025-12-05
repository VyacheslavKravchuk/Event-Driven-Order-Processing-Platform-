package com.inovexx.order_service.dto;

import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.entity.OrderItem;
import com.inovexx.order_service.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * DTO for {@link Order}
 */
public record OrderDto(@NotNull Long inventoryId,
                       @NotNull Long customerId,
                       @NotNull OrderStatus status,
                       @NotNull List<OrderItem> orderItems) {
}