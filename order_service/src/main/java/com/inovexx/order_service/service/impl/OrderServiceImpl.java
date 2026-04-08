package com.inovexx.order_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.order_service.client.InventoryClient;
import com.inovexx.order_service.client.UserClient;
import com.inovexx.order_service.dto.OrderDto;
import com.inovexx.order_service.dto.OrderStatusUpdatePayload;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.entity.OrderItem;
import com.inovexx.order_service.enums.EventStatus;
import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.events.OutboxEvent;
import com.inovexx.order_service.events.payload.OrderCancelledEvent;
import com.inovexx.order_service.events.payload.OrderCreatedEvent;
import com.inovexx.order_service.events.payload.OrderPaidEvent;
import com.inovexx.order_service.exception.OrderNotFoundException;
import com.inovexx.order_service.mapper.OrderMapper;
import com.inovexx.order_service.repository.OrderRepository;
import com.inovexx.order_service.repository.OutboxRepository;
import com.inovexx.order_service.service.OrderProcessSagaService;
import com.inovexx.order_service.service.OrderService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String EVENT_START_ORDER_SAGA = "START_GRPC_SAGA";
    private static final String EVENT_START_CANCEL_SAGA = "START_CANCEL_SAGA";
    private static final String EVENT_ORDER_PAID = "ORDER_PAID";
    private static final String EVENT_ORDER_CANCELLED = "ORDER_CANCELLED";
    private static final String EVENT_ORDER_STATUS_UPDATED = "ORDER_STATUS_UPDATED";

    private static final String REASON_USER_REQUEST = "USER_REQUEST";
    private static final String REASON_CANCELLATION_REQUESTED = "CANCELLATION_REQUESTED";

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS;

    static {
        Map<OrderStatus, Set<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);

        transitions.put(OrderStatus.NEW,
                EnumSet.of(OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED, OrderStatus.CANCELLATION_REQUESTED));

        transitions.put(OrderStatus.IN_PROGRESS,
                EnumSet.of(OrderStatus.RESERVED, OrderStatus.CANCELLED, OrderStatus.CANCELLATION_REQUESTED));

        transitions.put(OrderStatus.RESERVED,
                EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED, OrderStatus.CANCELLATION_REQUESTED));

        transitions.put(OrderStatus.PAID,
                EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED, OrderStatus.CANCELLATION_REQUESTED));

        transitions.put(OrderStatus.SHIPPED,
                EnumSet.of(OrderStatus.COMPLETED));

        transitions.put(OrderStatus.CANCELLATION_REQUESTED,
                EnumSet.of(OrderStatus.CANCELLED));

        transitions.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        transitions.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));

        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final InventoryClient inventoryClient;
    private final UserClient userClient;
    private final ObjectMapper objectMapper;
    private final OutboxRepository outboxRepository;
    private final OrderProcessSagaService orderProcessSagaService;

    /**
     * Быстрое создание заказа:
     * - сохраняем заказ со статусом NEW
     * - фиксируем email snapshot
     * - создаем outbox-событие на старт order saga
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDto createOrder(OrderDto orderDto) {
        log.info("Инициация заказа для пользователя {}", orderDto.userId());

        String customerEmail = fetchUserEmailSnapshot(orderDto.userId());

        Order order = orderMapper.orderDtoToOrder(orderDto);
        order.setCustomerEmail(customerEmail);
        order.setStatus(OrderStatus.NEW);
        order.setOrderDate(OffsetDateTime.now());

        if (order.getOrderItems() != null) {
            order.getOrderItems().forEach(item -> item.setOrder(order));
        }

        Order savedOrder = orderRepository.save(order);

        saveToOutbox(
                savedOrder.getOrderId(),
                EVENT_START_ORDER_SAGA,
                toOrderCreatedEvent(savedOrder)
        );

        return orderMapper.orderToOrderDto(savedOrder);
    }

    /**
     * Основная saga заказа:
     * 1) резервируем товары
     * 2) списываем баланс
     * 3) публикуем ORDER_PAID
     *
     * Если возникает ошибка, сначала пытаемся компенсировать сразу.
     * Если компенсация не удалась полностью — переводим заказ в CANCELLATION_REQUESTED
     * и запускаем отдельную cancel-saga через Outbox.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processOrderSaga(Long orderId) {
        log.info("[ORDER-SAGA] Запуск для заказа #{}", orderId);

        boolean movedToProgress =
                orderProcessSagaService.updateOrderStatusIfNew(orderId, OrderStatus.IN_PROGRESS);

        if (!movedToProgress) {
            log.warn("[ORDER-SAGA] Заказ #{} уже обрабатывается, отменен или завершен. Пропуск.", orderId);
            return;
        }

        Order order = getOrderOrThrow(orderId);

        List<OrderItem> successfullyReservedItems = new ArrayList<>();
        boolean paymentCaptured = false;

        try {
            ensureCancellationNotRequested(orderId);

            for (OrderItem item : order.getOrderItems()) {
                ensureCancellationNotRequested(orderId);

                boolean reserved = inventoryClient.reserveStock(item.getProductId(), item.getQuantity());
                if (!reserved) {
                    log.warn("[ORDER-SAGA] Недостаточно остатков для товара {} в заказе #{}",
                            item.getProductId(), orderId);

                    compensateAndCancelOrRequestAsync(
                            order,
                            successfullyReservedItems,
                            paymentCaptured,
                            "OUT_OF_STOCK: productId=" + item.getProductId()
                    );
                    return;
                }

                successfullyReservedItems.add(item);
            }

            orderProcessSagaService.updateOrderStatusAtomic(orderId, OrderStatus.RESERVED);

            ensureCancellationNotRequested(orderId);

            boolean paid = userClient.deductBalance(order.getUserId(), order.getTotalAmount(), orderId);
            if (!paid) {
                log.warn("[ORDER-SAGA] Недостаточно средств для заказа #{}", orderId);

                compensateAndCancelOrRequestAsync(
                        order,
                        successfullyReservedItems,
                        false,
                        "INSUFFICIENT_FUNDS"
                );
                return;
            }

            paymentCaptured = true;
            orderProcessSagaService.updateOrderStatusAtomic(orderId, OrderStatus.PAID);

            if (isCancellationRequested(orderId)) {
                log.warn("[ORDER-SAGA] Во время обработки заказа #{} была запрошена отмена", orderId);

                compensateAndCancelOrRequestAsync(
                        getOrderOrThrow(orderId),
                        successfullyReservedItems,
                        true,
                        REASON_USER_REQUEST
                );
                return;
            }

            Order finalOrder = getOrderOrThrow(orderId);

            saveToOutbox(
                    finalOrder.getOrderId(),
                    EVENT_ORDER_PAID,
                    toOrderPaidEvent(finalOrder)
            );

            log.info("[ORDER-SAGA] Заказ #{} успешно завершен и оплачен", orderId);

        } catch (CancellationRequestedException e) {
            log.warn("[ORDER-SAGA] Для заказа #{} запрошена отмена: {}", orderId, e.getMessage());

            compensateAndCancelOrRequestAsync(
                    getOrderOrThrow(orderId),
                    successfullyReservedItems,
                    paymentCaptured,
                    REASON_USER_REQUEST
            );
        } catch (Exception e) {
            log.error("[ORDER-SAGA] Критическая ошибка для заказа #{}: {}", orderId, e.getMessage(), e);

            compensateAndCancelOrRequestAsync(
                    getOrderOrThrow(orderId),
                    successfullyReservedItems,
                    paymentCaptured,
                    "SYSTEM_FAILURE: " + e.getMessage()
            );
        }
    }

    /**
     * Отдельная cancel-saga.
     * Ее должен дергать воркер/планировщик по Outbox-событию START_CANCEL_SAGA.
     *
     * Важно:
     * - refundBalance(...) и compensateReservation(...) должны быть ИДЕМПОТЕНТНЫМИ по orderId
     * - иначе безопасной distributed-компенсации не получится
     */

    public void processCancelSaga(Long orderId, String reason) {
        log.info("[CANCEL-SAGA] Запуск для заказа #{} с причиной {}", orderId, reason);

        Order order = getOrderOrThrow(orderId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("[CANCEL-SAGA] Заказ #{} уже отменен. Пропуск.", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.CANCELLATION_REQUESTED
                && order.getStatus() != OrderStatus.RESERVED
                && order.getStatus() != OrderStatus.PAID
                && order.getStatus() != OrderStatus.IN_PROGRESS) {
            log.warn("[CANCEL-SAGA] Заказ #{} в статусе {}, cancel-saga не применима",
                    orderId, order.getStatus());
            return;
        }

        try {
            refundIfNeeded(order);
            releaseAllItems(order.getOrderItems(), orderId);

            Order current = getOrderOrThrow(orderId);
            current.setStatus(OrderStatus.CANCELLED);
            Order cancelledOrder = orderRepository.save(current);

            saveToOutbox(
                    cancelledOrder.getOrderId(),
                    EVENT_ORDER_CANCELLED,
                    toOrderCancelledEvent(cancelledOrder, reason)
            );

            log.info("[CANCEL-SAGA] Заказ #{} успешно отменен", orderId);

        } catch (Exception e) {
            log.error("[CANCEL-SAGA] Не удалось завершить отмену заказа #{}: {}", orderId, e.getMessage(), e);
            throw e;
        }
    }

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
        return orderRepository.findById(orderId)
                .map(orderMapper::orderToOrderDto);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDto updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = getOrderOrThrow(orderId);

        validateStatusTransition(order.getStatus(), newStatus);

        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        var payload = new OrderStatusUpdatePayload(
                updatedOrder.getOrderId(),
                newStatus.name(),
                updatedOrder.getCustomerEmail(),
                OffsetDateTime.now()
        );

        saveToOutbox(updatedOrder.getOrderId(), EVENT_ORDER_STATUS_UPDATED, payload);

        return orderMapper.orderToOrderDto(updatedOrder);
    }

    /**
     * Физическое удаление заказов запрещено.
     * Для заказов нужен аудит и согласованность с внешними системами.
     */
    @Override
    @Transactional
    public void deleteOrderById(Long orderId) {
        throw new UnsupportedOperationException(
                "Physical order deletion is disabled. Use cancelOrder() or archive the order."
        );
    }

    /**
     * Пользовательская отмена заказа.
     *
     * Логика:
     * - NEW -> сразу CANCELLED
     * - IN_PROGRESS / RESERVED / PAID -> CANCELLATION_REQUESTED + START_CANCEL_SAGA
     * - CANCELLED / CANCELLATION_REQUESTED -> идемпотентный no-op
     * - SHIPPED / COMPLETED -> ошибка
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Заказ не найден: " + orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Заказ {} уже отменен. Повторная отмена игнорируется.", orderId);
            return;
        }

        if (order.getStatus() == OrderStatus.CANCELLATION_REQUESTED) {
            log.info("Для заказа {} отмена уже запрошена. Повторная отмена игнорируется.", orderId);
            return;
        }

        validateCancelTransition(order);

        if (order.getStatus() == OrderStatus.NEW) {
            order.setStatus(OrderStatus.CANCELLED);
            Order cancelledOrder = orderRepository.save(order);

            saveToOutbox(
                    cancelledOrder.getOrderId(),
                    EVENT_ORDER_CANCELLED,
                    toOrderCancelledEvent(cancelledOrder, REASON_USER_REQUEST)
            );

            log.info("Заказ {} отменен синхронно из статуса NEW", orderId);
            return;
        }

        order.setStatus(OrderStatus.CANCELLATION_REQUESTED);
        Order requestedOrder = orderRepository.save(order);

        saveToOutbox(
                requestedOrder.getOrderId(),
                EVENT_START_CANCEL_SAGA,
                new CancelRequestPayload(
                        requestedOrder.getOrderId(),
                        requestedOrder.getUserId(),
                        requestedOrder.getCustomerEmail(),
                        requestedOrder.getStatus().name(),
                        REASON_USER_REQUEST,
                        OffsetDateTime.now()
                )
        );

        log.info("Для заказа {} создан запрос на отмену через cancel-saga", orderId);
    }

    private void compensateAndCancelOrRequestAsync(Order order,
                                                   List<OrderItem> reservedItems,
                                                   boolean paymentCaptured,
                                                   String reason) {
        Long orderId = order.getOrderId();

        try {
            if (paymentCaptured) {
                refundPayment(order);
            }

            releaseAllItems(reservedItems, orderId);

            Order current = getOrderOrThrow(orderId);
            current.setStatus(OrderStatus.CANCELLED);
            Order cancelledOrder = orderRepository.save(current);

            saveToOutbox(
                    cancelledOrder.getOrderId(),
                    EVENT_ORDER_CANCELLED,
                    toOrderCancelledEvent(cancelledOrder, reason)
            );

            log.info("[COMPENSATION] Заказ #{} успешно компенсирован и отменен", orderId);

        } catch (Exception compensationException) {
            log.error("[COMPENSATION] Не удалось полностью компенсировать заказ #{}: {}",
                    orderId, compensationException.getMessage(), compensationException);

            requestAsyncCancellation(orderId, reason);

            log.warn("[COMPENSATION] Для заказа #{} запущена async cancel-saga", orderId);
        }
    }

    private void requestAsyncCancellation(Long orderId, String reason) {
        Order current = getOrderOrThrow(orderId);

        if (current.getStatus() != OrderStatus.CANCELLATION_REQUESTED
                && current.getStatus() != OrderStatus.CANCELLED) {
            current.setStatus(OrderStatus.CANCELLATION_REQUESTED);
            current = orderRepository.save(current);
        }

        saveToOutbox(
                current.getOrderId(),
                EVENT_START_CANCEL_SAGA,
                new CancelRequestPayload(
                        current.getOrderId(),
                        current.getUserId(),
                        current.getCustomerEmail(),
                        current.getStatus().name(),
                        reason,
                        OffsetDateTime.now()
                )
        );
    }

    private void releaseAllItems(List<OrderItem> items, Long orderId) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (OrderItem item : items) {
            inventoryClient.compensateReservation(
                    item.getProductId(),
                    item.getQuantity(),
                    orderId
            );

            log.info("[COMPENSATION] Резерв освобожден: orderId={}, productId={}, qty={}",
                    orderId, item.getProductId(), item.getQuantity());
        }
    }

    /**
     * Важно: refundBalance(...) должен быть идемпотентным.
     * Если платеж по orderId не был списан, метод должен безопасно вернуть true / no-op.
     */
    private void refundIfNeeded(Order order) {
        refundPayment(order);
    }

    private void refundPayment(Order order) {
        boolean refunded = userClient.refundBalance(
                order.getUserId(),
                order.getTotalAmount(),
                order.getOrderId()
        );

        if (!refunded) {
            throw new IllegalStateException("Не удалось вернуть деньги по заказу " + order.getOrderId());
        }

        log.info("[COMPENSATION] Деньги возвращены по заказу {}", order.getOrderId());
    }

    private void ensureCancellationNotRequested(Long orderId) {
        if (isCancellationRequested(orderId)) {
            throw new CancellationRequestedException("Cancellation was requested for orderId=" + orderId);
        }
    }

    private boolean isCancellationRequested(Long orderId) {
        return orderRepository.findById(orderId)
                .map(Order::getStatus)
                .filter(status -> status == OrderStatus.CANCELLATION_REQUESTED)
                .isPresent();
    }

    private void validateCancelTransition(Order order) {
        if (order.getStatus() == OrderStatus.SHIPPED) {
            throw new IllegalStateException("Нельзя отменить заказ, который уже отправлен");
        }

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Нельзя отменить уже завершенный заказ");
        }
    }

    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        if (currentStatus == newStatus) {
            return;
        }

        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Collections.emptySet());
        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                    "Недопустимый переход статуса: " + currentStatus + " -> " + newStatus
            );
        }

        log.debug("Переход статуса {} -> {} разрешен", currentStatus, newStatus);
    }

    private Order getOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * Outbox должен писаться строго и единообразно.
     * Если запись не удалась — падает вся транзакция.
     */
    private void saveToOutbox(Long orderId, String eventType, Object payload) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setOrderId(orderId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setProcessed(false);
            event.setStatus(EventStatus.PENDING);
            event.setRetryCount(0);
            event.setNextAttemptAt(OffsetDateTime.now());

            outboxRepository.save(event);

            log.debug("Outbox событие {} успешно сохранено для заказа {}", eventType, orderId);
        } catch (Exception e) {
            log.error("Критическая ошибка сохранения в Outbox для заказа {}: {}", orderId, e.getMessage(), e);
            throw new IllegalStateException(
                    "Не удалось сохранить событие в Outbox для заказа "
                            + orderId + ", eventType=" + eventType, e
            );
        }
    }

    private String fetchUserEmailSnapshot(Long userId) {
        try {
            String email = userClient.getUserEmail(userId);

            if (email == null || email.isBlank()) {
                log.error("[GRPC] User service вернул пустой email для userId={}", userId);
                throw new IllegalStateException("Email пользователя не найден");
            }

            return email;
        } catch (Exception e) {
            log.error("[GRPC] Ошибка при получении профиля пользователя {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Сервис пользователей временно недоступен", e);
        }
    }

    private OrderCreatedEvent toOrderCreatedEvent(Order order) {
        return new OrderCreatedEvent(
                order.getOrderId(),
                order.getUserId(),
                order.getCustomerEmail(),
                order.getTotalAmount(),
                order.getOrderDate(),
                order.getStatus().name()
        );
    }

    private OrderPaidEvent toOrderPaidEvent(Order order) {
        return new OrderPaidEvent(
                order.getOrderId(),
                order.getUserId(),
                order.getCustomerEmail(),
                order.getTotalAmount(),
                order.getOrderDate(),
                order.getStatus().name()
        );
    }

    private OrderCancelledEvent toOrderCancelledEvent(Order order, String reason) {
        return new OrderCancelledEvent(
                order.getOrderId(),
                order.getUserId(),
                order.getCustomerEmail(),
                order.getTotalAmount(),
                order.getOrderDate(),
                order.getStatus().name(),
                reason
        );
    }

    private record CancelRequestPayload(
            Long orderId,
            Long userId,
            String customerEmail,
            String status,
            String reason,
            OffsetDateTime requestedAt
    ) {
    }

    private static class CancellationRequestedException extends RuntimeException {
        public CancellationRequestedException(String message) {
            super(message);
        }
    }
}