package com.inovexx.order_service.service;

import com.inovexx.order_service.enums.OrderStatus;

import java.util.UUID;

public interface OrderProcessSagaService {

    void completeOrderSuccessfully(UUID orderId);

    void updateOrderStatusAtomic(UUID orderId, OrderStatus status);

}
