package com.inovexx.inventory_service.config;

import com.inovexx.inventory_service.dto.ProductEvent;
import com.inovexx.inventory_service.entity.Inventory;
import com.inovexx.inventory_service.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductEventListener {

    private final InventoryRepository inventoryRepository;

    @KafkaListener(topics = "product.events", groupId = "inventory-group")
    public void handleProductEvent(ProductEvent event) {
        log.info("Received event: {} for product: {}", event.eventType(), event.productId());

        switch (event.eventType()) {
            case "CREATED" -> createInventoryEntry(event.productId());
            case "DELETED" -> deleteInventoryEntry(event.productId());
            case "UPDATED" -> log.debug("Product info updated, no action needed for stock");
            default -> log.warn("Unknown event type: {}", event.eventType());
        }
    }

    private void createInventoryEntry(String productId) {
        // Проверяем, нет ли уже такой записи (защита от дублей из Kafka)
        if (!inventoryRepository.existsByProductId(productId)) {
            Inventory inventory = new Inventory();
            inventory.setProductId(productId);
            inventory.setAvailableStock(0); // По умолчанию товара нет
            inventory.setReservedStock(0);
            inventoryRepository.save(inventory);
            log.info("Created inventory record for product: {}", productId);
        }
    }

    private void deleteInventoryEntry(String productId) {
        inventoryRepository.findByProductId(productId)
                .ifPresent(inventory -> {
                    inventoryRepository.delete(inventory);
                    log.info("Deleted inventory record for product: {}", productId);
                });
    }

}
