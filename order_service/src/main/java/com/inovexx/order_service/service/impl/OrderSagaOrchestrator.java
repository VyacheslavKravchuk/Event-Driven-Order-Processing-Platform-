package com.inovexx.order_service.service.impl;

import com.inovexx.order_service.client.grpc_client.InventoryClient;
import com.inovexx.order_service.client.grpc_client.UserClient;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.enums.CancellationReason;
import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.enums.OutboxEventType;
import com.inovexx.order_service.enums.PaymentStatus;
import com.inovexx.order_service.events.payload.OrderPaidEvent;
import com.inovexx.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private final OrderRepository orderRepository;
    private final OrderStateService orderStateService;
    private final InventoryClient inventoryClient;
    private final UserClient userClient;
    private final OutboxService outboxService;

    public void handleOrderCreated(UUID orderId) {
        log.info("Получено событие создания заказа. Запуск саги для заказа ID: {}", orderId);
        Order order = orderStateService.getForUpdate(orderId);

        if (order.getStatus() != OrderStatus.NEW) {
            log.info("Заказ {} уже обработан или находится в процессе обработки, текущий статус={}", orderId, order.getStatus());
            return;
        }

        log.debug("Перевод заказа {} в статус IN_PROGRESS", orderId);
        orderStateService.transition(orderId, OrderStatus.NEW, OrderStatus.IN_PROGRESS);

        try {
            reserveInventory(orderId);
            chargePayment(orderId);
            commitInventory(orderId);
            //orderStateService.markCompleted(orderId);

            log.info("Сага для заказа {} успешно завершена", orderId);
        } catch (CancellationRequestedException ex) {
            log.warn("В процессе обработки заказа {} был запрошен возврат/отмена", orderId);
            compensate(orderId, CancellationReason.USER_REQUEST);
        } catch (Exception ex) {
            log.error("Сбой выполнения саги для заказа {}. Запуск процесса компенсации", orderId, ex);
            orderStateService.requestCancellation(orderId, CancellationReason.TECHNICAL_FAILURE);
        }
    }

    private void reserveInventory(UUID orderId) {
        log.info("[Шаг 1] Резервирование товаров на складе для заказа {}", orderId);
        Order order = orderStateService.getForUpdate(orderId);
        ensureNotCancellationRequested(order);

        boolean reserved = inventoryClient.reserveBatchStock(
                orderId.toString(),
                order.getOrderItems(),
                "order-service:reserve:" + orderId
        );

        if (!reserved) {
            log.warn("Не удалось зарезервировать товары для заказа {}: недостаточно товара на складе", orderId);
            orderStateService.requestCancellation(orderId,
                    CancellationReason.INSUFFICIENT_STOCK);
            throw new IllegalStateException("Inventory reservation failed");
        }

        log.info("Товары для заказа {} успешно зарезервированы", orderId);
        orderStateService.markReserved(orderId);
    }

    private void chargePayment(UUID orderId) {
        log.info("[Шаг 2] Списание средств для заказа {}", orderId);
        Order order = orderStateService.getForUpdate(orderId);
        ensureNotCancellationRequested(order);

        boolean charged = userClient.deductBalance(
                order.getUserId(),
                order.getTotalAmount(),
                orderId.toString()
        );

        if (!charged) {
            log.warn("Списание средств для заказа {} отклонено: недостаточно баланса", orderId);
            compensateReservationOnly(orderId, CancellationReason.PAYMENT_FAILED);
            throw new IllegalStateException("Payment failed");
        }

        log.info("Оплата для заказа {} успешно списана. Создание Outbox-события ORDER_PAID", orderId);
        orderStateService.markPaid(orderId);
        outboxService.saveEvent(orderId, OutboxEventType.ORDER_PAID, new OrderPaidEvent(
                order.getOrderId(),
                order.getUserId(),
                order.getCustomerEmail(),
                order.getTotalAmount(),
                order.getOrderDate(),
                OrderStatus.PAID.name()
        ));
    }

    private void commitInventory(UUID orderId) {
        log.info("[Шаг 3] Подтверждение списания товаров (Commit) для заказа {}", orderId);
        Order order = orderStateService.getForUpdate(orderId);
        ensureNotCancellationRequested(order);

        boolean committed = inventoryClient.commitBatchReduction(
                orderId.toString(),
                "order-service:commit:" + orderId
        );

        if (!committed) {
            log.error("Критическая ошибка: Склад не смог подтвердить списание для заказа {}", orderId);
            compensate(orderId, CancellationReason.INVENTORY_COMMIT_FAILED);
            throw new IllegalStateException("Inventory commit failed");
        }

        log.info("Списание товаров для заказа {} успешно подтверждено", orderId);
        orderStateService.markInventoryCommitted(orderId);
    }

    private void compensateReservationOnly(UUID orderId, CancellationReason reason) {
        log.info("Запуск частичной компенсации (только отмена брони склада) для заказа {} по причине: {}", orderId, reason);
        Order order = orderStateService.getForUpdate(orderId);

        if (orderStateService.hasActiveReservation(order)) {
            boolean released = inventoryClient.cancelBatchReservation(
                    orderId.toString(),
                    order.getOrderItems(),
                    "order-service:cancel:" + orderId
            );

            if (!released) {
                log.error("Не удалось снять бронь на складе при компенсации заказа {}", orderId);
                orderStateService.requestCancellation(orderId, reason);
                throw new IllegalStateException("Failed to release reservation");
            }

            log.info("Бронь на складе для заказа {} успешно снята", orderId);
            orderStateService.markReservationReleased(orderId);
        }

        log.info("Заказ {} успешно переведен в статус отмены", orderId);
        orderStateService.markCancelled(orderId, reason);
    }

    private void compensate(UUID orderId, CancellationReason reason) {
        log.info("Запуск полной компенсации (склад + оплата) для заказа {} по причине: {}", orderId, reason);
        Order order = orderStateService.getForUpdate(orderId);

        if (order.getPaymentStatus() == PaymentStatus.CHARGED) {
            log.info("Инициирован возврат средств пользователю {} для заказа {}", order.getUserId(), orderId);
            boolean refunded = userClient.refundBalance(
                    order.getUserId(),
                    order.getTotalAmount(),
                    orderId.toString()
            );
            if (!refunded) {
                log.error("Критический сбой: не удалось вернуть деньги пользователю {} для заказа {}", order.getUserId(), orderId);
                orderStateService.requestCancellation(orderId, reason);
                throw new IllegalStateException("Refund failed");
            }
            log.info("Возврат средств для заказа {} успешно выполнен", orderId);
            orderStateService.markRefunded(orderId);
        }

        Order refreshed = orderStateService.getForUpdate(orderId);
        if (orderStateService.hasActiveReservation(refreshed)) {
            log.info("Инициировано снятие брони на складе для заказа {}", orderId);
            boolean released = inventoryClient.cancelBatchReservation(
                    orderId.toString(),
                    refreshed.getOrderItems(),
                    "order-service:cancel:" + orderId
            );
            if (!released) {
                log.error("Не удалось снять бронь на складе при полной компенсации заказа {}", orderId);
                orderStateService.requestCancellation(orderId, reason);
                throw new IllegalStateException("Reservation release failed");
            }
            log.info("Бронь на складе для заказа {} успешно снята", orderId);
            orderStateService.markReservationReleased(orderId);
        }

        log.info("Полная компенсация завершена. Заказ {} отменен", orderId);
        orderStateService.markCancelled(orderId, reason);
    }

    private void ensureNotCancellationRequested(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLATION_REQUESTED) {
            log.warn("Обнаружен параллельный запрос на отмену транзакции для заказа {}", order.getOrderId());
            throw new CancellationRequestedException("Cancellation requested for order " + order.getOrderId());
        }
    }

    private static class CancellationRequestedException extends RuntimeException {
        public CancellationRequestedException(String message) {
            super(message);
        }
    }
}
