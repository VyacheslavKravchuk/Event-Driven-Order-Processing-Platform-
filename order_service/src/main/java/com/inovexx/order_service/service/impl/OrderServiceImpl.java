package com.inovexx.order_service.service.impl;


import com.inovexx.order_service.config.WebClientConfig;
import com.inovexx.order_service.dto.*;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.entity.OrderItem;
import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.exception.InvalidStockLevelException;
import com.inovexx.order_service.exception.ProductNotFoundException;
import com.inovexx.order_service.mapper.OrderMapper;
import com.inovexx.order_service.repository.OrderRepository;
import com.inovexx.order_service.service.KafkaProducerService;
import com.inovexx.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final KafkaProducerService kafkaProducerService;
    private final WebClientConfig webClientConfig;
    private final String inventoryServiceUrl = "http://localhost:8084/inventory/";

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

    @Transactional
    public OrderDto createOrder(OrderDto orderDto) {

        logger.info("Начало создания продукта: {}", orderDto);

        List<OrderItem> orderItems = orderDto.orderItems().stream()
                .map(itemDto -> {
                    OrderItem orderItem = new OrderItem();
                    orderItem.setInventoryId(itemDto.getInventoryId());
                    orderItem.setQuantity(itemDto.getQuantity());
                    // Получаем цену из product-service
                    orderItem.setPrice(BigDecimal.ONE); //Вместо 1 нужно получить цену от product-service.
                    return orderItem;
                })
                .collect(Collectors.toList());

        // Рассчитываем общую сумму заказа
        BigDecimal totalAmount = orderItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);


        // 3. Резервирование запасов (через inventory-service)
        // Взаимодействие с inventory-service для резервирования запасов
        List<OrderRequestInInventory> reservationRequests = orderDto.orderItems().stream()
                .map(item -> new OrderRequestInInventory(item.getInventoryId(), item.getQuantity()))
                .collect(Collectors.toList());

        // Используем WebClient для отправки POST-запроса в inventory-service
        Mono<Boolean> inventoryCheck = webClientConfig.webClient()// Используем имя сервиса
                .post().uri("http://inventory-service/api/product")
                .bodyValue(reservationRequests)
                .retrieve()
                .bodyToMono(Boolean.class); // Ожидаем ответ true/false или другой

        // Создание заказа
        Order order = new Order();
        order.setStatus(OrderStatus.NEW);
        order.setOrderDate(OffsetDateTime.now());
        order.setTotalAmount(totalAmount);
        order.setOrderItems(orderItems);

        Order savedOrder = orderRepository.save(order);

        kafkaProducerService.sendMessage("order-created", savedOrder.getOrderId());

        return orderMapper.orderToOrderDto(order);
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

        // Отправка сообщения в Kafka об изменении статуса
        kafkaProducerService.sendMessage("order-status-updates",  updatedOrder.getOrderId());

        return orderMapper.orderToOrderDto(updatedOrder);
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
        logger.info("Удаление записи заказа с ID: {}", orderId);
        orderRepository.deleteById(orderId);
        logger.info("Запись заказа с ID: {} успешно удалена", orderId);
    }
}
