package com.inovexx.order_service.service.impl;

import com.inovexx.order_service.dto.OrderStatusUpdatePayload;
import com.inovexx.order_service.entity.CancellationDetails;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.entity.OrderItem;
import com.inovexx.order_service.enums.CancellationReason;
import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.enums.OutboxEventType;
import com.inovexx.order_service.enums.PaymentStatus;
import com.inovexx.order_service.exception.OrderNotFoundException;
import com.inovexx.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderStateService {

    private final OrderRepository orderRepository;
    private final OutboxService outboxService;


    @Transactional(readOnly = true)
    public boolean hasActiveReservation(Order order) {
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return false;
        }

        return order.getOrderItems().stream()
                .anyMatch(item -> {
                    int reservedQty = item.getReservedQuantity() == null ? 0 : item.getReservedQuantity();
                    return reservedQty > 0 && !item.isReservationReleased();
                });
    }

    public void transition(UUID orderId, OrderStatus from, OrderStatus to) {
        Order order = getForUpdate(orderId);

        if (order.getStatus() != from) {
            log.error("Сбой смены статуса заказа {}: ожидался статус {}, но текущий статус {}", orderId, from, order.getStatus());
            throw new IllegalStateException(
                    "Expected status " + from + " but was " + order.getStatus()
            );
        }

        validateTransition(from, to);
        log.info("Смена статуса заказа {}: {} -> {}", orderId, from, to);
        order.setStatus(to);
        orderRepository.save(order);
        outboxService.saveEvent(
                orderId,
                OutboxEventType.ORDER_STATUS_UPDATED,
                new OrderStatusUpdatePayload(
                        order.getOrderId(),
                        to.name(),
                        order.getCustomerEmail(),
                        OffsetDateTime.now()
                )
        );
    }

    public void markReserved(UUID orderId) {
        Order order = getForUpdate(orderId);

        validateTransition(order.getStatus(), OrderStatus.RESERVED);
        log.info("Обновление статуса заказа {}: {} -> RESERVED. Фиксация зарезервированного количества товаров", orderId, order.getStatus());
        order.setStatus(OrderStatus.RESERVED);

        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                item.setReservationReleased(false);
                if (item.getReservedQuantity() == null || item.getReservedQuantity() == 0) {
                    item.setReservedQuantity(item.getQuantity());
                }
            }
        }

        orderRepository.save(order);
    }

    public void markPaid(UUID orderId) {
        Order order = getForUpdate(orderId);

        validateTransition(order.getStatus(), OrderStatus.PAID);
        log.info("Обновление статуса заказа {}: {} -> PAID. Статус оплаты изменен на CHARGED", orderId, order.getStatus());
        order.setStatus(OrderStatus.PAID);
        order.setPaymentStatus(PaymentStatus.CHARGED);

        orderRepository.save(order);
    }

    public void markRefunded(UUID orderId) {
        Order order = getForUpdate(orderId);
        log.info("Фиксация возврата средств для заказа {}. Статус оплаты: REFUNDED", orderId);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        orderRepository.save(order);
    }

    public void markInventoryCommitted(UUID orderId) {
        Order order = getForUpdate(orderId);
        log.debug("Фиксация окончательного списания со склада для заказа {}. Снятие флагов удержания брони", orderId);
        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                item.setReservationReleased(true);
            }
        }
        orderRepository.save(order);
    }

    public void markCompleted(UUID orderId) {
        Order order = getForUpdate(orderId);

        if (order.getStatus() != OrderStatus.PAID) {
            log.error("Невозможно завершить заказ {}: текущий статус {}, а должен быть PAID", orderId, order.getStatus());
            throw new IllegalStateException("Only PAID order can be completed");
        }

        log.info("Заказ {} успешно выполнен и закрыт (STATUS -> COMPLETED)", orderId);
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);
        outboxService.saveEvent(
                orderId,
                OutboxEventType.ORDER_COMPLETED,
                new OrderStatusUpdatePayload(
                        order.getOrderId(),
                        OrderStatus.COMPLETED.name(),
                        order.getCustomerEmail(),
                        OffsetDateTime.now()
                )
        );
    }

    public void requestCancellation(UUID orderId, CancellationReason reason) {
        Order order = getForUpdate(orderId);

        if (order.getStatus() == OrderStatus.CANCELLED ||
                order.getStatus() == OrderStatus.CANCELLATION_REQUESTED) {
            log.debug("Запрос на отмену заказа {} проигнорирован: заказ уже отменен или в процессе отмены", orderId);
            return;
        }

        log.warn("Инициирован запрос на отмену заказа {} по причине: {}", orderId, reason);
        ensureCancellationDetails(order);
        order.setStatus(OrderStatus.CANCELLATION_REQUESTED);
        order.getCancellationDetails().setReason(reason);
        order.getCancellationDetails().setCancelledAt(OffsetDateTime.now());

        orderRepository.save(order);

        log.debug("Отправка Outbox-события ORDER_CANCELLATION_REQUESTED для заказа {}", orderId);
        outboxService.saveEvent(
                orderId,
                OutboxEventType.START_CANCEL_SAGA,
                new CancelRequestPayload(
                        order.getOrderId(),
                        order.getUserId(),
                        order.getCustomerEmail(),
                        order.getStatus().name(),
                        reason,
                        OffsetDateTime.now()
                )
        );
    }

    public void markCancelled(UUID orderId, CancellationReason reason) {
        Order order = getForUpdate(orderId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.debug("Перевод заказа {} в статус CANCELLED пропущен: заказ уже отменен", orderId);
            return;
        }

        log.warn("Заказ {} окончательно отменен (STATUS -> CANCELLED). Причина: {}", orderId, reason);
        ensureCancellationDetails(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.getCancellationDetails().setReason(reason);
        order.getCancellationDetails().setCancelledAt(OffsetDateTime.now());

        orderRepository.save(order);

        log.debug("Отправка Outbox-события ORDER_CANCELLED для заказа {}", orderId);
        outboxService.saveEvent(orderId, OutboxEventType.ORDER_CANCELLED,
                new OrderCancelledPayload(
                        order.getOrderId(),
                        order.getUserId(),
                        order.getCustomerEmail(),
                        order.getTotalAmount(),
                        order.getOrderDate(),
                        order.getStatus().name(),
                        reason,
                        mapItems(order)
                ));
    }

    public void markReservationReleased(UUID orderId) {
        Order order = getForUpdate(orderId);
        log.info("Снятие бронирования товаров (компенсация) для заказа {}", orderId);

        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                item.setReservationReleased(true);
            }
        }

        orderRepository.save(order);
    }

    private void validateTransition(OrderStatus from, OrderStatus to) {
        Map<OrderStatus, Set<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);
        transitions.put(OrderStatus.NEW,
                EnumSet.of(OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED, OrderStatus.CANCELLATION_REQUESTED));
        transitions.put(OrderStatus.IN_PROGRESS,
                EnumSet.of(OrderStatus.RESERVED, OrderStatus.CANCELLED, OrderStatus.CANCELLATION_REQUESTED));
        transitions.put(OrderStatus.RESERVED,
                EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED, OrderStatus.CANCELLATION_REQUESTED));
        transitions.put(OrderStatus.PAID,
                EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.CANCELLATION_REQUESTED));
        transitions.put(OrderStatus.CANCELLATION_REQUESTED,
                EnumSet.of(OrderStatus.CANCELLED));
        transitions.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        transitions.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));

        Set<OrderStatus> allowed = transitions.getOrDefault(from, Collections.emptySet());
        if (!allowed.contains(to)) {
            log.error("Запрещенный переход конечного автомата статусов: {} -> {}", from, to);
            throw new IllegalStateException("Illegal transition: " + from + " -> " + to);
        }
    }

    private record ItemPayload(String productId, Integer quantity) {}

    public record OrderCancelledPayload(
            UUID orderId,
            Long userId,
            String customerEmail,
            BigDecimal totalAmount,
            OffsetDateTime orderDate,
            String status,
            CancellationReason reason,
            List<ItemPayload> items
    ) {}
    public record CancelRequestPayload(
            UUID orderId,
            Long userId,
            String customerEmail,
            String status,
            CancellationReason reason,
            OffsetDateTime requestedAt
    ) {}

    private Order getOrderOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private void ensureCancellationDetails(Order order) {
        if (order.getCancellationDetails() == null) {
            order.setCancellationDetails(new CancellationDetails());
        }
    }

    private List<ItemPayload> mapItems(Order order) {
        if (order.getOrderItems() == null) {
            return List.of();
        }

        return order.getOrderItems().stream()
                .map(item -> new ItemPayload(
                        String.valueOf(item.getProductId()),
                        item.getQuantity()
                ))
                .toList();
    }

    public Order getForUpdate(UUID orderId) {
        log.trace("Запрос заказа для обновления с ID: {}", orderId);
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}