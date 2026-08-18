package com.inovexx.order_service.service.news;

import com.inovexx.order_service.mapper.OrderMapper;
import com.inovexx.order_service.repository.OrderRepository;
import com.inovexx.order_service.service.impl.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderTransactionHandler {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OutboxService outboxService;

//    @Transactional
//    public OrderDto saveNewOrder(OrderDto orderDto, String customerEmail) {
//        Order order = orderMapper.orderDtoToOrder(orderDto);
//        order.setCustomerEmail(customerEmail);
//        order.setStatus(OrderStatus.NEW);
//        order.setPaymentStatus(PaymentStatus.NOT_CHARGED);
//        order.setOrderDate(OffsetDateTime.now());
//
//        // Устанавливаем связи для OrderItems
//        if (order.getOrderItems() != null) {
//            order.getOrderItems().forEach(item -> {
//                item.setOrder(order);
//                item.setReservationReleased(false);
//            });
//        }
//
//        Order savedOrder = orderRepository.save(order);
//
//        // Сохраняем событие в Outbox (в той же транзакции)
//        outboxService.saveEvent(
//                savedOrder.getOrderId(),
//                OutboxEventType.ORDER_CREATED,
//                new OrderCreatedEvent(
//                        savedOrder.getOrderId(),
//                        savedOrder.getUserId(),
//                        savedOrder.getCustomerEmail(),
//                        savedOrder.getTotalAmount(),
//                        savedOrder.getOrderDate(),
//                        savedOrder.getStatus().name()
//                )
//        );
//
//        return orderMapper.orderToOrderDto(savedOrder);
//    }
}