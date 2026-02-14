package com.inovexx.inventory_service.dto;


public record InventoryDto (

        String productId,

        int availableStock,

        int reservedStock
){}
