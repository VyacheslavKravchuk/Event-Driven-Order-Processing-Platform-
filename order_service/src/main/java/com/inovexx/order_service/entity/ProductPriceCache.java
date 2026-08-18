package com.inovexx.order_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "product_prices_cache")
@Getter
@Setter
public class ProductPriceCache {
    @Id
    private String productId;
    private String type;
    private BigDecimal price;
    private String name;
}

