package com.inovexx.inventory_service.dto;


public record InventoryDto (

        Long productId,

        int availableStock,

        int reservedStock
){}
