package com.inovexx.order_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.inovexx.order_service.dto.OrderDto;
import com.inovexx.order_service.enums.OrderStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface OrderService {

    List<OrderDto> findAll();

    public Optional<OrderDto> findById(Long productId);

    @Transactional
    OrderDto createOrder(OrderDto orderDto) throws JsonProcessingException;

    OrderDto updateOrderStatus(Long id, OrderStatus newStatus) throws JsonProcessingException;

    @Transactional
    void deleteOrderById(Long productId) throws JsonProcessingException;
}
