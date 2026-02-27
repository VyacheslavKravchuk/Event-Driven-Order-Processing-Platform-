package com.inovexx.inventory_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderItemDto(
        String productId,
        Integer quantity
) {}
