package com.inovexx.order_service.service.impl;

import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.enums.CancellationReason;
import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.enums.PaymentStatus;
import com.inovexx.order_service.exception.OrderNotFoundException;
import com.inovexx.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationOrderTxService {

    private final OrderRepository orderRepository;
    private final OrderStateService orderStateService;

    private final OrderCancellationOrchestrator orderCancellationOrchestrator;

    /**
     * Транзакционная пользовательская отмена заказа.
     *
     * Логика:
     * 1. Ищем заказ.
     * 2. Проверяем допустимость отмены.
     * 3. Если заказ можно отменить мгновенно - сразу переводим в CANCELLED.
     * 4. Если заказ уже в обработке - переводим в CANCELLATION_REQUESTED
     *    и публикуем событие для компенсационной saga.
     */
    @Transactional
    public void cancelOrder(UUID orderId, CancellationReason reason) {
        CancellationReason resolvedReason = resolveReason(reason);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        log.info(
                "Запрос на отмену заказа #{}. Текущий статус={}, причина={}",
                orderId,
                order.getStatus(),
                resolvedReason
        );

        // Идемпотентность: если уже отменен — просто выходим
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Заказ #{} уже находится в статусе CANCELLED. Повторная отмена не требуется", orderId);
            return;
        }

        // Идемпотентность: если отмена уже была запрошена — повторно ничего не запускаем
        if (order.getStatus() == OrderStatus.CANCELLATION_REQUESTED) {
            log.info("Для заказа #{} отмена уже запрошена ранее", orderId);
            return;
        }

        validateCancellationAllowed(order);

        if (canCancelSynchronously(order)) {
            log.info("Заказ #{} может быть отменен синхронно", orderId);
            orderStateService.markCancelled(orderId, resolvedReason);
            log.info("Заказ #{} успешно отменен синхронно", orderId);
            return;
        }

        log.info("Заказ #{} требует асинхронной отмены через saga", orderId);
        order.setStatus(OrderStatus.CANCELLATION_REQUESTED);
        //orderStateService.requestCancellation(orderId, resolvedReason);
        orderCancellationOrchestrator.handleCancellationRequested(orderId);
        log.info("Для заказа #{} успешно зарегистрирован запрос на отмену", orderId);
    }

    /**
     * Проверка, можно ли отменить заказ сразу, без компенсационной saga.
     *
     * Разрешаем синхронную отмену только если:
     * - заказ еще NEW
     * - деньги не списывались
     * - активной резервации нет
     */
    private boolean canCancelSynchronously(Order order) {
        return order.getStatus() == OrderStatus.NEW
                && order.getPaymentStatus() == PaymentStatus.NOT_CHARGED
                && !orderStateService.hasActiveReservation(order);
    }

    /**
     * Проверка бизнес-ограничений отмены.
     */
    private void validateCancellationAllowed(Order order) {
        OrderStatus status = order.getStatus();

        if (status == OrderStatus.SHIPPED) {
            throw new IllegalStateException("Нельзя отменить заказ, который уже отправлен");
        }

        if (status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Нельзя отменить уже завершенный заказ");
        }
    }

    /**
     * Если причина не передана из контроллера,
     * считаем, что это обычная отмена пользователем.
     */
    private CancellationReason resolveReason(CancellationReason reason) {
        return reason != null ? reason : CancellationReason.USER_REQUEST;
    }
}
