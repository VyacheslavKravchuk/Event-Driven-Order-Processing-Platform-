package com.inovexx.order_service.service.impl;

import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.repository.OrderRepository;
import com.inovexx.order_service.service.OrderProcessSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Service
@Slf4j
@RequiredArgsConstructor
public class OrderProcessSagaServiceImpl implements OrderProcessSagaService {

    private final OrderRepository orderRepository;


    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean updateOrderStatusIfNew(Long orderId, OrderStatus newStatus) {
        return orderRepository.findById(orderId)
                .filter(o -> o.getStatus() == OrderStatus.NEW)
                .map(o -> {
                    o.setStatus(newStatus);
                    orderRepository.save(o);
                    return true;
                }).orElse(false);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOrderStatusAtomic(Long orderId, OrderStatus status) {
        orderRepository.updateStatus(orderId, status);
    }
}
