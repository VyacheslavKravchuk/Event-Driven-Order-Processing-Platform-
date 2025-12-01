package com.inovexx.product_service.dto;

import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

public record ProductDto (

        String name,

        String description,

        @Field("price_usd")
        BigDecimal price,

        String category
){}
