package com.inovexx.inventory_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderEventDto(
        Long orderId,
        String status,

        // Указываем Jackson, как мапить поле из JSON заказа
        @JsonProperty("orderItems")
        List<OrderItemDto> items
) {}
