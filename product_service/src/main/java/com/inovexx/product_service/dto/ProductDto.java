package com.inovexx.product_service.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

@Data
@Builder
public class ProductDto {

    private String name;

    private String description;

    @Field("price_usd") 
    private BigDecimal price;

    private String category;
}
