package com.inovexx.inventory_service.service;

import com.inovexx.inventory_service.dto.InventoryDto;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface InventoryService {

    List<InventoryDto> findAll();

    public Optional<InventoryDto> findById(String productId);

    @Transactional
    InventoryDto create(InventoryDto inventoryDto);

    InventoryDto updateStock(String productId, int newStock);

    @Transactional
    void deleteById(String productId);
}
