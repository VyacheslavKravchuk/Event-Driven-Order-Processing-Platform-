package com.inovexx.inventory_service.service.impl;

import com.inovexx.inventory_service.dto.InventoryDto;
import com.inovexx.inventory_service.entity.Inventory;
import com.inovexx.inventory_service.exception.InvalidStockLevelException;
import com.inovexx.inventory_service.exception.ProductNotFoundException;
import com.inovexx.inventory_service.mapper.InventoryMapper;
import com.inovexx.inventory_service.repository.InventoryRepository;
import com.inovexx.inventory_service.service.InventoryService;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j // Используем аннотацию Lombok для логирования
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;

    @Override
    @Transactional(readOnly = true)
    public List<InventoryDto> findAll() {
        log.info("Получение всех записей инвентаря");
        return inventoryRepository.findAll()
                .stream()
                .map(inventoryMapper::inventoryToInventoryDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InventoryDto> findById(String productId) {
        // Очищаем ID от возможных пробелов, которые могут прийти из REST/Kafka
        String cleanId = productId.trim();
        log.info("Поиск записи инвентаря по productId: '{}'", cleanId);

        return inventoryRepository.findByProductId(cleanId)
                .map(inventoryMapper::inventoryToInventoryDto);
    }

    @Override
    @Transactional
    public InventoryDto create(InventoryDto inventoryDto) {
        log.info("Запрос на создание записи инвентаря для продукта: {}", inventoryDto.productId());

        // Проверяем, нет ли уже такой записи, чтобы избежать DuplicateKeyException
        inventoryRepository.findByProductId(inventoryDto.productId())
                .ifPresent(i -> {
                    throw new IllegalStateException("Запись для продукта " + inventoryDto.productId() + " уже существует");
                });

        Inventory inventory = inventoryMapper.inventoryDtoToInventory(inventoryDto);
        validateStockLevel(inventory.getAvailableStock());

        Inventory savedInventory = inventoryRepository.save(inventory);
        log.info("Запись успешно создана для productId: {}", savedInventory.getProductId());
        return inventoryMapper.inventoryToInventoryDto(savedInventory);
    }

    @Override
    @Transactional
    public InventoryDto updateStock(String productId, int newStock) {
        // КРИТИЧЕСКИЙ МОМЕНТ: очистка ID и поиск по бизнес-ключу productId
        String cleanId = productId.trim();
        log.info("Обновление запасов для продукта ID: '{}', новое количество: {}", cleanId, newStock);

        // Ищем строго по полю productId (не путать с внутренним _id MongoDB)
        Inventory existing = inventoryRepository.findByProductId(cleanId)
                .orElseThrow(() -> {
                    log.error("Продукт с ID: '{}' не найден в базе данных инвентаря", cleanId);
                    return new ProductNotFoundException(cleanId);
                });

        validateStockLevel(newStock);
        existing.setAvailableStock(newStock);

        Inventory updatedInventory = inventoryRepository.save(existing);
        log.info("Запасы для продукта ID: '{}' успешно обновлены до {}", cleanId, newStock);
        return inventoryMapper.inventoryToInventoryDto(updatedInventory);
    }

    @Override
    @Transactional
    public void deleteById(String productId) {
        String cleanId = productId.trim();
        log.info("Удаление записи инвентаря для productId: '{}'", cleanId);

        if (!inventoryRepository.existsByProductId(cleanId)) {
            throw new ProductNotFoundException(cleanId);
        }

        inventoryRepository.deleteByProductId(cleanId);
        log.info("Запись для productId: '{}' успешно удалена", cleanId);
    }

    /**
     * Унифицированная валидация уровня склада
     */
    private void validateStockLevel(int stock) {
        if (stock < 0) {
            log.warn("Валидация провалена: отрицательный сток {}", stock);
            throw new InvalidStockLevelException("Количество запасов не может быть отрицательным");
        }
    }
}
