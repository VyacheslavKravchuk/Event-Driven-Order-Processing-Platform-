package com.inovexx.inventory_service.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor // Add this
@ToString
public class InventoryDto {

    private Long productId;

    private int availableStock;

    private int reservedStock;
}
