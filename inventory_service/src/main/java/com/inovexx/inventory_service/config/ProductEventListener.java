package com.inovexx.inventory_service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.inventory_service.dto.ProductEvent;
import com.inovexx.inventory_service.entity.Inventory;
import com.inovexx.inventory_service.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductEventListener {

    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper; // Добавляем ObjectMapper для безопасного парсинга

    @KafkaListener(topics = "product.events", groupId = "inventory-group")
    public void handleProductEvent(String message) { // Принимаем String вместо объекта
        try {
            // Ручная десериализация сообщения
            ProductEvent event = objectMapper.readValue(message, ProductEvent.class);

            log.info("Received event: {} for product: {}", event.eventType(), event.productId());

            processEvent(event);

        } catch (JsonProcessingException e) {
            // Если пришел невалидный JSON, мы просто логируем ошибку и не "вешаем" consumer
            log.error("Failed to deserialize ProductEvent from Kafka message: {}", message, e);
        } catch (Exception e) {
            log.error("Unexpected error while processing product event", e);
        }
    }

    private void processEvent(ProductEvent event) {
        switch (event.eventType()) {
            case "CREATED" -> createInventoryEntry(event.productId());
            case "DELETED" -> deleteInventoryEntry(event.productId());
            case "UPDATED" -> log.debug("Product info updated, no action needed for stock for product: {}", event.productId());
            default -> log.warn("Unknown event type: {} for product: {}", event.eventType(), event.productId());
        }
    }

    @Transactional
    protected void createInventoryEntry(String productId) {
        // Проверяем, нет ли уже такой записи (идемпотентность — защита от дублей)
        if (!inventoryRepository.existsByProductId(productId)) {
            Inventory inventory = new Inventory();
            inventory.setProductId(productId);
            inventory.setAvailableStock(0); // Начальный сток всегда 0
            inventory.setReservedStock(0);

            inventoryRepository.save(inventory);
            log.info("Created inventory record for product: {}", productId);
        } else {
            log.info("Inventory record already exists for product: {}, skipping creation", productId);
        }
    }

    @Transactional
    protected void deleteInventoryEntry(String productId) {
        inventoryRepository.findByProductId(productId)
                .ifPresentOrElse(
                        inventory -> {
                            inventoryRepository.delete(inventory);
                            log.info("Deleted inventory record for product: {}", productId);
                        },
                        () -> log.warn("Attempted to delete inventory for product: {}, but record was not found", productId)
                );
    }
}