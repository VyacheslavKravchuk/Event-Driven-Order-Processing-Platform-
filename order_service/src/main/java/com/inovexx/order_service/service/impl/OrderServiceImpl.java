package com.inovexx.order_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.order_service.client.InventoryClient;
import com.inovexx.order_service.client.UserClient;
import com.inovexx.order_service.dto.OrderDto;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.entity.OrderItem;
import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.events.OutboxEvent;
import com.inovexx.order_service.exception.OrderNotFoundException;
import com.inovexx.order_service.mapper.OrderMapper;
import com.inovexx.order_service.repository.OrderRepository;
import com.inovexx.order_service.repository.OutboxRepository;
import com.inovexx.order_service.service.OrderProcessSagaService;
import com.inovexx.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final InventoryClient inventoryClient;
    private final UserClient userClient;
    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;
    private final OrderProcessSagaService orderProcessSagaService;

    /**
     * ШАГ 1: Быстрая инициация.
     * Сохраняем заказ со статусом NEW и создаем событие в Outbox.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDto createOrder(OrderDto orderDto) {
        log.info("Инициация заказа для пользователя {}", orderDto.userId());

        Order order = orderMapper.orderDtoToOrder(orderDto);
        order.setStatus(OrderStatus.NEW);
        order.setOrderDate(OffsetDateTime.now());

        if (order.getOrderItems() != null) {
            order.getOrderItems().forEach(item -> item.setOrder(order));
        }
        Order savedOrder = orderRepository.save(order);

        saveToOutbox(savedOrder.getOrderId(), "START_GRPC_SAGA",
                orderMapper.orderToOrderDto(savedOrder));

        return orderMapper.orderToOrderDto(savedOrder);
    }

    /**
     * ШАГ 2: Основной метод САГИ (Асинхронный).
     * Вызывается планировщиком из Outbox. Выполняет gRPC вызовы к Inventory и User сервисам.
     * Идемпотентен по статусу IN_PROGRESS.
     */
    @Transactional(rollbackFor = Exception.class)
    public void processOrderSaga(Long orderId) {
        log.info("[SAGA] Запуск gRPC саги для заказа #{}", orderId);

        // 1. Идемпотентный захват статуса (предотвращаем двойной запуск)
        boolean movedToProgress = orderProcessSagaService.updateOrderStatusIfNew(orderId, OrderStatus.IN_PROGRESS);
        if (!movedToProgress) {
            log.warn("[SAGA] Заказ #{} уже в обработке или завершен. Пропуск.", orderId);
            return;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        List<OrderItem> successfullyReservedItems = new ArrayList<>();

        try {
            // ШАГ 2.1: Резервирование товаров (gRPC, идемпотентно)
            for (OrderItem item : order.getOrderItems()) {
                boolean reserved = inventoryClient
                        .reserveStock(item.getProductId(), item.getQuantity());
                if (reserved) {
                    successfullyReservedItems.add(item);
                } else {
                    handleSagaFailure(orderId, successfullyReservedItems, order,
                            "Нет достаточного запаса для товара: " + item.getProductId());
                    return;
                }
            }

            // Обновляем статус после резервирования (опционально, для гранулярности)
            orderProcessSagaService.updateOrderStatusAtomic(orderId, OrderStatus.RESERVED);

            // ШАГ 2.2: Оплата (gRPC, идемпотентно по orderId)
            boolean paid = userClient.deductBalance(order.getUserId(), order.getTotalAmount(), orderId);

            if (!paid) {
                handleSagaFailure(orderId, successfullyReservedItems,
                        order, "Недостаточно средств на балансе");
                return;
            }

            // Успех: финальный статус и событие
            orderProcessSagaService.updateOrderStatusAtomic(orderId, OrderStatus.PAID);
            Order finalOrder = orderRepository.findById(orderId).orElseThrow(); // Reload для актуального состояния
            saveToOutbox(orderId, "ORDER_PAID", orderMapper.orderToOrderDto(finalOrder));
            log.info("[SAGA] Заказ #{} успешно завершен (оплачен).", orderId);

        } catch (Exception e) {
            log.error("[SAGA] Критическая ошибка для заказа #{}: {}", orderId, e.getMessage(), e);
            Order currentOrder = orderRepository.findById(orderId).orElseThrow();
            handleSagaFailure(orderId,
                    successfullyReservedItems, currentOrder,
                    "Системный сбой: " + e.getMessage());
            // Не пробрасываем дальше: сага завершена компенсацией, планировщик отметит Outbox как processed
        }
    }

//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public boolean updateOrderStatusIfNew(Long orderId, OrderStatus newStatus) {
//        return orderRepository.findById(orderId)
//                .filter(o -> o.getStatus() == OrderStatus.NEW)
//                .map(o -> {
//                    o.setStatus(newStatus);
//                    orderRepository.save(o);
//                    return true;
//                }).orElse(false);
//    }
//
//    @Transactional(propagation = Propagation.REQUIRES_NEW)
//    public void updateOrderStatusAtomic(Long orderId, OrderStatus status) {
//        orderRepository.updateStatus(orderId, status);
//    }

    /**
     * Метод компенсации (лучшие усилия).
     */
    private void handleSagaFailure(Long orderId,
                                   List<OrderItem> itemsToRelease,
                                   Order order, String reason) {
        log.warn("[COMPENSATION] Откат заказа #{} по причине: {}", orderId, reason);

        // Компенсация резерваций (идемпотентно по orderId)
        for (OrderItem item : itemsToRelease) {
            try {
                inventoryClient.compensateReservation(item.getProductId(),
                        item.getQuantity(), orderId);
                log.info("[COMPENSATION] Товар {} освобожден для заказа {}",
                        item.getProductId(), orderId);
            } catch (Exception e) {
                log.error("[CRITICAL] Не удалось компенсировать товар {} для заказа {}: {}",
                        item.getProductId(), orderId, e.getMessage(), e);
                // В проде: отдельный Outbox для dead-letter компенсаций
            }
        }

        // Финальный статус
        orderProcessSagaService.updateOrderStatusAtomic(orderId, OrderStatus.CANCELLED);

        // Событие для уведомления (с причиной)
        Order cancelledOrder = orderRepository.findById(orderId).orElseThrow();
        OrderDto dto = orderMapper.orderToOrderDto(cancelledOrder);
        saveToOutbox(orderId, "ORDER_CANCELLED", dto);
        log.info("[SAGA] Заказ #{} отменен с компенсацией.", orderId);
    }

    /**
     * Улучшенный метод сохранения в Outbox с поддержкой мониторинга.
     * Не бросает исключения (best-effort).
     */
    private void saveToOutbox(Long orderId, String eventType, Object payload) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setOrderId(orderId);
            event.setEventType(eventType); // Исправлено: используем параметр
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setCreatedAt(OffsetDateTime.now());

            // Поля для планировщика/мониторинга
            event.setProcessed(false);
            event.setRetryCount(0);
            event.setNextAttemptAt(OffsetDateTime.now());

            outboxRepository.save(event);
            log.debug("Outbox событие {} сохранено для заказа {}", eventType, orderId);
        } catch (Exception e) {
            log.error("Критическая ошибка сохранения Outbox для заказа {} (eventType={}): {}",
                    orderId, eventType, e.getMessage(), e);
            // Dead-letter или алерт в проде
        }
    }

    // --- Стандартные методы CRUD ---

    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> findAll() {
        return orderRepository.findAll().stream()
                .map(orderMapper::orderToOrderDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderDto> findById(Long orderId) {
        return orderRepository.findById(orderId).map(orderMapper::orderToOrderDto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDto updateOrderStatus(Long orderId, OrderStatus newStatus)
            throws JsonProcessingException {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        validateStatusTransition(order.getStatus(), newStatus);
        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        saveToOutbox(orderId, "ORDER_STATUS_UPDATED", updatedOrder);
        return orderMapper.orderToOrderDto(updatedOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrderById(Long orderId) throws JsonProcessingException {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        saveToOutbox(orderId, "ORDER_DELETED", order);
        orderRepository.delete(order);
    }

    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        if (currentStatus == newStatus) return;

        if (currentStatus == OrderStatus.COMPLETED || currentStatus == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Заказ в финальном статусе: " + currentStatus);
        }

        // Дополнительная логика валидации переходов...
        log.debug("Переход статуса {} -> {} разрешен", currentStatus, newStatus);
    }
}