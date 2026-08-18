package com.inovexx.order_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.inovexx.order_service.dto.OrderDto;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.enums.CancellationReason;
import com.inovexx.order_service.enums.OrderStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderService {

    List<OrderDto> findAll();

    public Optional<OrderDto> findById(UUID orderId);

    @Transactional
    OrderDto createOrder(OrderDto orderDto) throws JsonProcessingException;

    void processOrderSaga(UUID orderId);

    OrderDto updateOrderStatus(UUID id, OrderStatus newStatus) throws JsonProcessingException;

    @Transactional
    void deleteOrderById(UUID orderId) throws JsonProcessingException;

    void saveToOutbox(UUID orderId, String eventType, Object payload);

    void cancelOrder(UUID orderId, CancellationReason reason);

    //void processCancelSaga(UUID orderId, CancellationReason reason);
    void performSagaCancellation(Order order, CancellationReason reason);
}
