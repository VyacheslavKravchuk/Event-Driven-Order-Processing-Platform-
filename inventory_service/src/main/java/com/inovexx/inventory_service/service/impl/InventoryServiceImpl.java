package com.inovexx.inventory_service.service.impl;

import com.inovexx.inventory_service.dto.InventoryDto;
import com.inovexx.inventory_service.entity.Inventory;
import com.inovexx.inventory_service.exception.InvalidStockLevelException;
import com.inovexx.inventory_service.exception.ProductNotFoundException;
import com.inovexx.inventory_service.repository.InventoryRepository;
import com.inovexx.inventory_service.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private static final Logger logger = LoggerFactory.getLogger(InventoryServiceImpl.class);

    @Override
    @Transactional(readOnly = true)
    public List<InventoryDto> findAll() {
        logger.info("Получение всех записей инвентаря");
        List<InventoryDto> inventoryDtos = inventoryRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        logger.info("Найдено {} записей инвентаря", inventoryDtos.size());
        return inventoryDtos;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryDto> findById(Long productId) {
        logger.info("Поиск записи инвентаря по ID продукта: {}", productId);
        return inventoryRepository.findById(productId).map(this::toDto);
    }

    @Transactional
    @Override
    public InventoryDto create(InventoryDto inventoryDto) {
        logger.info("Создание новой записи инвентаря: {}", inventoryDto);

        Inventory inventory = toEntity(inventoryDto);
        validateInventory(inventory);

        Inventory savedInventory = inventoryRepository.save(inventory);
        logger.info("Запись инвентаря успешно создана с ID: {}", savedInventory.getProductId());
        return toDto(savedInventory);
    }

    @Override
    @Transactional
    public InventoryDto updateStock(Long productId, int newStock) {
        logger.info("Обновление запасов для продукта ID: {}, новое количество: {}", productId, newStock);

        Inventory existing = inventoryRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        validateStockLevel(newStock);
        existing.setAvailableStock(newStock);
        Inventory updatedInventory = inventoryRepository.save(existing);

        logger.info("Запасы для продукта ID: {} успешно обновлены", productId);
        return toDto(updatedInventory);
    }

    @Override
    @Transactional
    public void deleteById(Long productId) {
        logger.info("Удаление записи инвентаря с ID продукта: {}", productId);
        inventoryRepository.deleteById(productId);
        logger.info("Запись инвентаря с ID продукта: {} успешно удалена", productId);
    }

    private InventoryDto toDto(Inventory inventory) {
        return new InventoryDto(
                inventory.getProductId(),
                inventory.getAvailableStock(),
                inventory.getReservedStock()
        );
    }

    private Inventory toEntity(InventoryDto dto) {
        Inventory inventory = new Inventory();
        inventory.setProductId(dto.productId());
        inventory.setAvailableStock(dto.availableStock());
        inventory.setReservedStock(dto.reservedStock());
        return inventory;
    }

    private void validateInventory(Inventory inventory) {
        if (inventory.getAvailableStock() < 0) {
            logger.warn("Попытка создать запись инвентаря с отрицательным количеством запасов: {}", inventory.getAvailableStock());
            throw new InvalidStockLevelException("Количество запасов не может быть отрицательным");
        }
    }

    private void validateStockLevel(int newStock) {
        if (newStock < 0) {
            logger.warn("Попытка установить отрицательное количество запасов: {}", newStock);
            throw new InvalidStockLevelException("Количество запасов не может быть отрицательным");
        }
    }
}
