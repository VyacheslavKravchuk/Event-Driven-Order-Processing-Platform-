package com.inovexx.order_service.service;

import com.inovexx.order_service.enums.OrderStatus;

public interface OrderProcessSagaService {

    boolean updateOrderStatusIfNew(Long orderId, OrderStatus newStatus);

    void updateOrderStatusAtomic(Long orderId, OrderStatus status);

}
