package com.inovexx.order_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.order_service.client.InventoryClient;
import com.inovexx.order_service.client.UserClient;
import com.inovexx.order_service.dto.*;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.entity.OrderItem;
import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.events.OutboxEvent;
import com.inovexx.order_service.exception.OrderNotFoundException;
import com.inovexx.order_service.mapper.OrderMapper;
import com.inovexx.order_service.repository.OrderRepository;
import com.inovexx.order_service.repository.OutboxRepository;
import com.inovexx.order_service.service.KafkaProducerService;
import com.inovexx.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
    private final KafkaProducerService kafkaProducerService;

    @Value("${kafka.topic.order-events:order.events}")
    private String orderEventsTopicName;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDto createOrder(OrderDto orderDto)
            throws JsonProcessingException {
        log.info("Начало создания заказа для пользователя {}",
                orderDto.userId());

        // 1. Сохраняем заказ в локальную БД со статусом NEW
        Order order = orderMapper.orderDtoToOrder(orderDto);
        order.setStatus(OrderStatus.NEW);
        order.setOrderDate(OffsetDateTime.now());
        order.getOrderItems().forEach(item -> item.setOrder(order));

        Order savedOrder = orderRepository.save(order);
        List<OrderItem> successfullyReservedItems = new ArrayList<>();

        try {
            // 2. ШАГ: РЕЗЕРВИРОВАНИЕ (Inventory Service)
            for (OrderItem item : savedOrder.getOrderItems()) {
                boolean reserved = inventoryClient.
                        reserveStock(item.getProductId(), item.getQuantity());

                if (reserved) {
                    successfullyReservedItems.add(item);
                } else {
                    log.warn("Товара {} недостаточно. Отмена саги.",
                            item.getProductId());
                    handleFailure(savedOrder, successfullyReservedItems, false);
                    return orderMapper.orderToOrderDto(savedOrder);
                }
            }

            // 3. ШАГ: ОПЛАТА (User Service)
            boolean paid = userClient.deductBalance(
                    savedOrder.getUserId(),
                    savedOrder.getTotalAmount(),
                    savedOrder.getOrderId()
            );

            if (paid) {
                // УСПЕХ: Финализируем заказ
                savedOrder.setStatus(OrderStatus.PAID);
                log.info("Заказ #{} успешно оплачен и зарезервирован",
                        savedOrder.getOrderId());
            } else {
                // ОШИБКА ОПЛАТЫ: Откатываем склад
                log.warn("Оплата заказа #{} отклонена. Запуск компенсации склада.",
                        savedOrder.getOrderId());
                handleFailure(savedOrder, successfullyReservedItems, false);
            }

        } catch (Exception e) {
            log.error("Критический сбой при обработке заказа #{}: {}",
                    savedOrder.getOrderId(), e.getMessage());
            // Если упало исключение (например, gRPC таймаут), откатываем всё
            handleFailure(savedOrder, successfullyReservedItems, true);
            throw e; // Пробрасываем для отката транзакции в БД заказов
        }

        // 4. Запись в Outbox (событие для Notification Service и др.)
        saveToOutbox(savedOrder.getOrderId(),
                "ORDER_PAID",
                orderMapper.orderToOrderDto(savedOrder));

        return orderMapper.orderToOrderDto(savedOrder);
    }

    /**
     * Метод компенсации (Откат Саги)
     */
    private void handleFailure(Order order, List<OrderItem> itemsToRelease, boolean refundMoney) {
        order.setStatus(OrderStatus.CANCELLED);

        // Компенсируем склад
        itemsToRelease.forEach(item ->
                inventoryClient.compensateReservation(item.getProductId(), item.getQuantity())
        );

        // Если бы у нас была оплата до склада или она прошла частично - здесь был бы refundMoney
        // В текущей схеме склад первый, поэтому refundMoney понадобится при расширении логики

        log.info("Сага компенсирована для заказа #{}", order.getOrderId());
    }


    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> findAll() {
        log.info("Запрос на получение всех записей заказов");
        List<OrderDto> orderDtos = orderRepository.findAll()
                .stream()
                .map(orderMapper::orderToOrderDto)
                .collect(Collectors.toList());
        log.info("Успешно найдено {} записей заказов", orderDtos.size());
        return orderDtos;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderDto> findById(Long orderId) {
        log.info("Поиск заказа по идентификатору: {}", orderId);
        Optional<OrderDto> orderDto = orderRepository.findById(orderId).map(orderMapper::orderToOrderDto);

        if (orderDto.isPresent()) {
            log.info("Заказ с ID: {} успешно найден", orderId);
        } else {
            log.warn("Заказ с ID: {} не найден в базе данных", orderId);
        }
        return orderDto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDto updateOrderStatus(Long orderId, OrderStatus newStatus) throws JsonProcessingException {
        log.info("Запрос на обновление статуса заказа ID: {} на {}", orderId, newStatus);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.error("Ошибка обновления: заказ с ID: {} не найден", orderId);
                    return new OrderNotFoundException(orderId);
                });

        validateStatusTransition(order.getStatus(), newStatus);

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        log.info("Статус заказа ID: {} успешно изменен с {} на {}", orderId, oldStatus, newStatus);

        // Сохранение события обновления в Outbox
        saveToOutbox(updatedOrder.getOrderId(), "ORDER_STATUS_UPDATED", updatedOrder);

        return orderMapper.orderToOrderDto(updatedOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrderById(Long orderId) throws JsonProcessingException {
        log.info("Запрос на удаление заказа ID: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.error("Ошибка удаления: заказ с ID: {} не найден", orderId);
                    return new OrderNotFoundException(orderId);
                });

        // Сначала сохраняем событие удаления в Outbox (пока данные еще есть в БД)
        saveToOutbox(orderId, "ORDER_DELETED", order);

        orderRepository.delete(order);
        log.info("Заказ ID: {} успешно удален из базы данных и событие добавлено в Outbox", orderId);
    }

    /**
     * Вспомогательный метод для сохранения событий в таблицу Outbox.
     * Выполняется в рамках текущей транзакции.
     */
    private void saveToOutbox(Long orderId, String eventType, Object payload) throws JsonProcessingException {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setOrderId(orderId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setProcessed(false); // Всегда явно указываем, что еще не обработано

            outboxRepository.save(event);
            log.debug("Событие {} для заказа ID: {} сохранено в Outbox", eventType, orderId);
        } catch (JsonProcessingException e) {
            log.error("Критическая ошибка сериализации для заказа ID: {}: {}", orderId, e.getMessage());
            throw e; // Откатываем @Transactional метод createOrder
        }
    }

    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        log.debug("Проверка возможности перехода статуса: {} -> {}", currentStatus, newStatus);
        // Если статус не меняется, валидация не требуется
        if (currentStatus == newStatus) {
            return;
        }
        // Запрет на любые изменения, если заказ уже завершен или отменен
        if (currentStatus == OrderStatus.COMPLETED || currentStatus == OrderStatus.CANCELLED) {
            log.error("Попытка изменить финальный статус {} на {}", currentStatus, newStatus);
            throw new IllegalStateException("Нельзя изменить статус заказа, который уже находится в конечном состоянии: " + currentStatus);
        }
        switch (newStatus) {
            case RESERVED:
                if (currentStatus != OrderStatus.NEW) {
                    throw new IllegalStateException("Зарезервировать можно только новый заказ (текущий статус: " + currentStatus + ")");
                }
                break;
            case PAID:
                if (currentStatus != OrderStatus.RESERVED) {
                    throw new IllegalStateException("Оплатить можно только зарезервированный заказ (текущий статус: " + currentStatus + ")");
                }
                break;
            case SHIPPED:
                if (currentStatus != OrderStatus.PAID) {
                    throw new IllegalStateException("Отгрузить можно только оплаченный заказ (текущий статус: " + currentStatus + ")");
                }
                break;
            case COMPLETED:
                if (currentStatus != OrderStatus.SHIPPED) {
                    throw new IllegalStateException("Завершить можно только отгруженный заказ (текущий статус: " + currentStatus + ")");
                }
                break;
            case CANCELLED:
                // Логика: отменить можно на этапах до того, как заказ был отгружен клиенту
                if (currentStatus == OrderStatus.SHIPPED) {
                    throw new IllegalStateException("Нельзя отменить заказ, который уже передан в службу доставки/отгружен");
                }
                log.info("Заказ переводится в статус CANCELLED из состояния {}", currentStatus);
                break;
            case NEW:
                // Возврат в статус NEW обычно не предусмотрен логикой системы
                throw new IllegalStateException("Возврат заказа в начальный статус NEW невозможен");
            default:
                log.error("Обработка перехода в статус {} не реализована", newStatus);
                throw new IllegalArgumentException("Неизвестный целевой статус: " + newStatus);
        }

        log.info("Валидация перехода {} -> {} успешно пройдена", currentStatus, newStatus);
    }
}
