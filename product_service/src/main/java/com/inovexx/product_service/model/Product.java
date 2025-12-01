package com.inovexx.product_service.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

@Getter
@Setter
@ToString
@Document(collection = "products")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Product {

    @Id
    private String id;

    private String name;

    private String description;

    @Field("price_rub")
    private BigDecimal price;

    private String category;

}
