package com.inovexx.order_service.dto;

import java.math.BigDecimal;

public record OrderRequestInProduct(

        Long inventoryId,

        int quantity,

        BigDecimal pricePerUnit
){
}
