package com.inovexx.order_service.service.impl;

import com.inovexx.order_service.client.grpc_client.InventoryClient;
import com.inovexx.order_service.client.grpc_client.UserClient;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.enums.CancellationReason;
import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.enums.PaymentStatus;
import com.inovexx.order_service.exception.OrderNotFoundException;
import com.inovexx.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancellationOrchestrator {

    private final OrderRepository orderRepository;
    private final OrderStateService orderStateService;
    private final UserClient userClient;
    private final InventoryClient inventoryClient;



    /**
     * Главная точка входа для компенсационной саги отмены.
     *
     * Важно:
     * - метод должен быть идемпотентным
     * - метод НЕ должен держать долгую БД-транзакцию на время gRPC-вызовов
     * - локальные state changes делаются через OrderStateService
     */
    public void handleCancellationRequested(UUID orderId) {
        Order order = getOrder(orderId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Cancellation saga: заказ #{} уже CANCELLED, повторная обработка не требуется", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.CANCELLATION_REQUESTED) {
            log.warn(
                    "Cancellation saga: заказ #{} имеет статус {}, ожидался CANCELLATION_REQUESTED. Обработка пропущена",
                    orderId,
                    order.getStatus()
            );
            return;
        }

        CancellationReason reason = resolveCancellationReason(order);

        log.info(
                "Cancellation saga: старт компенсации для заказа #{} с причиной {}",
                orderId,
                reason
        );

        refundIfNeeded(orderId);
        releaseReservationIfNeeded(orderId);

        orderStateService.markCancelled(orderId, reason);

        log.info("Cancellation saga: заказ #{} успешно переведен в CANCELLED", orderId);
    }

    /**
     * Возвращаем деньги только если они реально были списаны.
     * Если заказ уже REFUNDED - ничего не делаем.
     */
    private void refundIfNeeded(UUID orderId) {
        Order order = getOrder(orderId);

        if (order.getPaymentStatus() == PaymentStatus.REFUNDED) {
            log.info("Cancellation saga: деньги по заказу #{} уже возвращены", orderId);
            return;
        }

        if (order.getPaymentStatus() != PaymentStatus.CHARGED) {
            log.info(
                    "Cancellation saga: refund для заказа #{} не требуется, paymentStatus={}",
                    orderId,
                    order.getPaymentStatus()
            );
            return;
        }

        log.info("Cancellation saga: выполняем refund для заказа #{}", orderId);

        boolean refunded = userClient.refundBalance(
                order.getUserId(),
                order.getTotalAmount(),
                orderId.toString()
        );

        if (!refunded) {
            throw new IllegalStateException("Не удалось вернуть деньги по заказу " + orderId);
        }

        orderStateService.markRefunded(orderId);

        log.info("Cancellation saga: refund успешно выполнен для заказа #{}", orderId);
    }

    /**
     * Снимаем резерв только если он активен.
     * Если резерва уже нет - повторно ничего не делаем.
     */
    private void releaseReservationIfNeeded(UUID orderId) {
        Order order = getOrder(orderId);

        if (!orderStateService.hasActiveReservation(order)) {
            log.info("Cancellation saga: активной резервации для заказа #{} нет", orderId);
            return;
        }

        log.info("Cancellation saga: снимаем reservation для заказа #{}", orderId);

        boolean released = inventoryClient.cancelBatchReservation(
                orderId.toString(),
                order.getOrderItems(),
                buildIdempotencyKey("cancel-reservation", orderId)
        );

        if (!released) {
            throw new IllegalStateException("Не удалось снять reservation для заказа " + orderId);
        }

        orderStateService.markReservationReleased(orderId);

        log.info("Cancellation saga: reservation успешно снята для заказа #{}", orderId);
    }

    private Order getOrder(UUID orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private CancellationReason resolveCancellationReason(Order order) {
        if (order.getCancellationDetails() != null
                && order.getCancellationDetails().getReason() != null) {
            return order.getCancellationDetails().getReason();
        }
        return CancellationReason.USER_REQUEST;
    }

    private String buildIdempotencyKey(String operation, UUID orderId) {
        return "order-service:" + operation + ":" + orderId;
    }
}
