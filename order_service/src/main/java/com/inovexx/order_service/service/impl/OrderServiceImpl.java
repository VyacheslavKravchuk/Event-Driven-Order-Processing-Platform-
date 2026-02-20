package com.inovexx.order_service.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.order_service.client.InventoryClient;
import com.inovexx.order_service.dto.*;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.entity.OrderItem;
import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.events.OutboxEvent;
import com.inovexx.order_service.exception.ProductNotFoundException;
import com.inovexx.order_service.mapper.OrderMapper;
import com.inovexx.order_service.repository.OrderRepository;
import com.inovexx.order_service.service.KafkaProducerService;
import com.inovexx.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final InventoryClient inventoryClient;
    private final ObjectMapper objectMapper;

    private final KafkaProducerService kafkaProducerService;
    @Value("${kafka.topic.order-events:order.events}")
    private String orderEventsTopicName;

    private static final Logger logger = LoggerFactory.getLogger(OrderServiceImpl.class);


    @Override
    @Transactional(readOnly = true)
    public List<OrderDto> findAll() {
        logger.info("Получение всех записей заказов");
        List<OrderDto> orderDtos = orderRepository.findAll()
                .stream()
                .map(orderMapper::orderToOrderDto)
                .collect(Collectors.toList());
        logger.info("Найдено {} записей заказов", orderDtos.size());
        return orderDtos;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderDto> findById(Long orderId) {
        logger.info("Поиск записи заказа по ID: {}", orderId);
        return orderRepository.findById(orderId).map(orderMapper::orderToOrderDto);
    }

    @Override
    @Transactional
    public OrderDto createOrder(OrderDto orderDto) {
        // 1. Превращаем DTO в сущность Entity
        Order order = orderMapper.orderDtoToOrder(orderDto);

        // 2. Устанавливаем начальный статус и сохраняем (чтобы получить orderId)
        order.setStatus(OrderStatus.NEW);
        order.setOrderDate(OffsetDateTime.now());

        // Важно: связываем OrderItem с Order через хелпер (если есть) или вручную
        order.getOrderItems().forEach(item -> item.setOrder(order));

        Order savedOrder = orderRepository.save(order);

        // 3. Вызываем inventory-service через gRPC для каждого товара
        boolean allReserved = true;
        for (OrderItem item : savedOrder.getOrderItems()) {
            boolean reserved = inventoryClient.reserveStock(item.getProductId(), item.getQuantity());
            if (!reserved) {
                allReserved = false;
                break;
            }
        }

        // 4. Финализируем статус
        if (allReserved) {
            savedOrder.setStatus(OrderStatus.RESERVED);
        } else {
            savedOrder.setStatus(OrderStatus.CANCELLED);
        }

     // 2. СОХРАНЕНИЕ В OUTBOX (в той же транзакции!)
        OutboxEvent event = new OutboxEvent();
        event.setOrderId(savedOrder.getOrderId().toString());
        event.setEventType("ORDER_CREATED");
        event.setPayload(objectMapper.writeValueAsString(resultDto)); // Превращаем в JSON
        outboxRepository.save(event);

        // Возвращаем результат обратно в виде DTO
        return orderMapper.orderToOrderDto(orderRepository.save(savedOrder));
    }

    @Override
    @Transactional
    public OrderDto updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ProductNotFoundException("Заказ не найден с id: " + orderId));

        //  Валидация перехода статуса (можно сделать более сложную логику)
        validateStatusTransition(order.getStatus(), newStatus);

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        Order updatedOrder = orderRepository.save(order);

        OrderDto orderDto = orderMapper.orderToOrderDto(updatedOrder);

        // Это позволит notification-service отправить письмо "Ваш заказ оплачен"
        kafkaProducerService.sendMessage(orderEventsTopicName, orderDto);

        return orderDto;
    }

    private void validateStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        // Пример простой валидации. Можно расширить логику.
        switch (newStatus) {
            case RESERVED:
                if (currentStatus != OrderStatus.NEW) {
                    throw new IllegalStateException("Нельзя зарезервировать заказ, который не в статусе NEW");
                }
                break;
            case PAID:
                if (currentStatus != OrderStatus.RESERVED) {
                    throw new IllegalStateException("Нельзя оплатить заказ, который не в статусе RESERVED");
                }
                break;
        }
    }

    @Override
    @Transactional
    public void deleteOrderById(Long orderId) {
        // Сначала ищем, чтобы было что отправить в уведомлении
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ProductNotFoundException("Заказ не найден"));
        OrderDto deletedOrderDto = orderMapper.orderToOrderDto(order);

        orderRepository.delete(order);

        // Отправляем событие удаления
        kafkaProducerService.sendMessage(orderEventsTopicName, deletedOrderDto);
    }
}
