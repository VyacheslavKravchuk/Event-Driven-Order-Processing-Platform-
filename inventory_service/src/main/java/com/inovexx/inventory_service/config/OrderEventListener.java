package com.inovexx.inventory_service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.inventory_service.dto.OrderEventDto;
import com.inovexx.inventory_service.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper;

//    @KafkaListener(topics = "${kafka.topic.order-events:order.events}", groupId = "inventory-group")
//    @Transactional
//    public void handleOrderEvent(String message) {
//        try {
//            // Десериализация в наш новый record
//            OrderEventDto event = objectMapper.readValue(message, OrderEventDto.class);
//            log.info("Событие заказа получено. ID: {}, Статус: {}", event.orderId(), event.status());
//
//            switch (event.status()) {
//                case "CANCELLATION_REQUESTED" -> log.info("Заказ #{} отменен. ", event.orderId());
//                case "PAID" -> log.info("Заказ #{} оплачен. Резерв подтвержден.", event.orderId());
//                default -> log.debug("Статус {} не требует действий от склада", event.status());
//            }
//
//        } catch (JsonProcessingException e) {
//            log.error("Ошибка парсинга JSON заказа: {}", e.getMessage());
//        }
//    }

//    private void processOrderCancellation(OrderEventDto event) {
//        log.info("Возврат товаров для отмененного заказа #{}", event.orderId());
//
//        // Используем метод items() нашего record
//        event.items().forEach(item -> {
//            inventoryRepository.findByProductId(item.productId())
//                    .ifPresentOrElse(inventory -> {
//
//                                int restoredStock = inventory.getAvailableStock() + item.quantity();
//                                inventory.setAvailableStock(restoredStock);
//
//                                // ОБЯЗАТЕЛЬНО уменьшаем резерв, который создали через gRPC ранее
//                                int newReserved = Math.max(0, inventory.getReservedStock() - item.quantity());
//                                inventory.setReservedStock(newReserved);
//
//                                inventoryRepository.save(inventory);
//                                log.info("Товар {} ({} шт.) возвращен в сток", item.productId(), item.quantity());
//                            },
//                            () -> log.error("ТОВАР НЕ НАЙДЕН: {} при отмене заказа #{}", item.productId(), event.orderId())
//                    );
//        });
//    }
}


