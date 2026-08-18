package com.inovexx.order_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.order_service.dto.OrderDto;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.entity.OrderItem;
import com.inovexx.order_service.enums.*;
import com.inovexx.order_service.events.OutboxEvent;
import com.inovexx.order_service.events.payload.OrderCreatedEvent;
import com.inovexx.order_service.mapper.OrderMapper;
import com.inovexx.order_service.repository.OrderRepository;
import com.inovexx.order_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreationTxService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderDto createOrderTx(OrderDto orderDto) {
        log.info("Начало процесса создания заказа для пользователя с ID: {}", orderDto.userId());

        Order order = orderMapper.orderDtoToOrder(orderDto);
        order.setCustomerEmail(orderDto.customerEmail());
        order.setStatus(OrderStatus.NEW);
        order.setPaymentStatus(PaymentStatus.NOT_CHARGED);
        order.setOrderDate(OffsetDateTime.now());

        if (order.getOrderItems() != null) {
            log.debug("Привязка позиций заказа к основному заказу. Количество позиций: {}", order.getOrderItems().size());
            for (OrderItem item : order.getOrderItems()) {
                item.setOrder(order);
                item.setReservationReleased(false);
                if (item.getReservedQuantity() == null) {
                    item.setReservedQuantity(0);
                }
            }
        }

        Order saved = orderRepository.save(order);
        log.info("Заказ успешно сохранен в БД. Сгенерирован ID заказа: {}", saved.getOrderId());

        log.debug("Формирование Outbox-события ORDER_CREATED для транзакционной отправки в Kafka");
        OutboxEvent event = new OutboxEvent();
        event.setOrderId(saved.getOrderId());
        event.setEventType(OutboxEventType.ORDER_CREATED);
        event.setPayload(writePayload(new OrderCreatedEvent(
                saved.getOrderId(),
                saved.getUserId(),
                saved.getCustomerEmail(),
                saved.getTotalAmount(),
                saved.getOrderDate(),
                saved.getStatus().name()
        )));
        event.setProcessed(false);
        event.setStatus(EventStatus.PENDING);
        event.setRetryCount(0);
        event.setNextAttemptAt(OffsetDateTime.now());

        outboxRepository.save(event);
        log.info("Outbox-событие для заказа ID: {} успешно сохранено в статусе PENDING", saved.getOrderId());

        return orderMapper.orderToOrderDto(saved);
    }

    private String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Ошибка при сериализации полезной нагрузки (payload) для Outbox-события: {}", e.getMessage(), e);
            throw new IllegalStateException("Не удалось сериализовать payload для Outbox", e);
        }
    }
}