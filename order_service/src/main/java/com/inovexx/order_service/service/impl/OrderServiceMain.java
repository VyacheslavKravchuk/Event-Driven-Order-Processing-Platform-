package com.inovexx.order_service.service.impl;

import com.inovexx.order_service.client.grpc_client.UserClient;
import com.inovexx.order_service.dto.OrderDto;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.enums.CancellationReason;
import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.exception.OrderNotFoundException;
import com.inovexx.order_service.mapper.OrderMapper;
import com.inovexx.order_service.repository.OrderRepository;
import com.inovexx.order_service.service.news.OrderTransactionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceMain {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final UserClient userClient;
    private final OrderTransactionHandler txHandler;
    private final OrderStateService stateService;


    /**
     * Отмена заказа.
     * Реализует логику идемпотентности и выбора между синхронной и асинхронной отменой.
     */

    @Transactional
    public void cancelOrder(UUID orderId, CancellationReason reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 1. Идемпотентность: если уже отменен, ничего не делаем
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Заказ #{} уже отменен", orderId);
            return;
        }

        // 2. Валидация: нельзя отменить отправленный или завершенный заказ
        validateCancelPossibility(order);

        // 3. Выбор стратегии отмены
        if (canCancelSynchronously(order)) {
            // Синхронная отмена для новых, не обработанных заказов
            stateService.markCancelled(orderId, reason != null ? reason : CancellationReason.USER_REQUEST);
            log.info("Заказ #{} отменен синхронно", orderId);
        } else {
            // Асинхронная отмена (через Сагу) для заказов в работе/оплаченных
            stateService.requestCancellation(orderId, reason != null ? reason : CancellationReason.USER_REQUEST);
            log.info("Запущен процесс асинхронной отмены для заказа #{}", orderId);
        }
    }

    @Transactional(readOnly = true)
    public List<OrderDto> findAll() {
        return orderRepository.findAll().stream()
                .map(orderMapper::orderToOrderDto)
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public Optional<OrderDto> findById(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(orderMapper::orderToOrderDto);
    }

    // --- Вспомогательные методы ---

    private String fetchUserEmailSnapshot(Long userId) {
        try {
            return userClient.getUserEmail(userId);
        } catch (Exception e) {
            log.error("User Service недоступен для userId {}: {}", userId, e.getMessage());
            throw new RuntimeException("Не удалось получить email пользователя", e);
        }
    }

    private boolean canCancelSynchronously(Order order) {
        // Заказ можно отменить мгновенно, если он NEW и по нему еще нет движения денег или резервов
        return order.getStatus() == OrderStatus.NEW && !stateService.hasActiveReservation(order);
    }

    private void validateCancelPossibility(Order order) {
        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Нельзя отменить заказ в статусе " + order.getStatus());
        }
    }

    public void deleteOrderById(UUID orderId) {
        throw new UnsupportedOperationException(
                "Physical order deletion is disabled. Use cancelOrder() or archive the order."
        );

    }

    public OrderDto updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        log.info("Запрос на ручное обновление статуса заказа #{} на {}", orderId, newStatus);

        // 1. Запрещаем ручную отмену через этот метод (для этого есть cancelOrder)
        if (newStatus == OrderStatus.CANCELLED || newStatus == OrderStatus.CANCELLATION_REQUESTED) {
            throw new IllegalStateException("Для отмены заказа используйте метод cancelOrder()");
        }

        // 2. Запрещаем ручную установку системных статусов Саги
        if (isSystemStatus(newStatus)) {
            throw new IllegalStateException("Статус " + newStatus + " устанавливается автоматически системой Сага");
        }

        Order orderWithOldStatus = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // 3. Выполняем переход через StateService (там сидит логика валидации ALLOWED_TRANSITIONS)
        stateService.transition(orderId, newStatus, orderWithOldStatus.getStatus());

        // 4. Возвращаем обновленный объект
        Order updatedOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return orderMapper.orderToOrderDto(updatedOrder);
    }

    /**
     * Проверка, является ли статус чисто системным (управляется только Сагой)
     */
    private boolean isSystemStatus(OrderStatus status) {
        return status == OrderStatus.RESERVED ||
                status == OrderStatus.PAID ||
                status == OrderStatus.IN_PROGRESS;
    }
}
