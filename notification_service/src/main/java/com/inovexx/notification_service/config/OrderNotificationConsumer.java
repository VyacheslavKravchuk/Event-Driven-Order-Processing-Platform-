package com.inovexx.notification_service.config;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.notification_service.dto.events.OrderCompletedEvent;
import com.inovexx.notification_service.service.impl.EmailServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationConsumer {

    private final EmailServiceImpl emailService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.events", groupId = "notification-service-group")
    public void handleOrderEvent(OrderCompletedEvent event) {
        // 1. Пропускаем технический статус
        if ("IN_PROGRESS".equals(event.status())) {
            log.debug("Пропуск уведомления для статуса IN_PROGRESS для заказа #{}", event.orderId());
            return;
        }

        try {
            // 2. Формируем динамическую тему письма
            String subject = switch (event.status()) {
                case "NEW" -> "Ваш заказ #" + event.orderId() + " принят";
                case "RESERVED" -> "Товары по заказу #" + event.orderId() + " забронированы";
                case "PAID" -> "Оплата заказа #" + event.orderId() + " получена";
                case "SHIPPED" -> "Ваш заказ #" + event.orderId() + " уже в пути!";
                case "COMPLETED" -> "Заказ #" + event.orderId() + " успешно доставлен";
                case "CANCELLED" -> "Обновление: Заказ #" + event.orderId() + " отменен";
                default -> "Обновление по вашему заказу #" + event.orderId();
            };

            Map<String, Object> model = objectMapper.convertValue(event, new TypeReference<>() {});
            model.put("supportEmail", "support@inovexx.com");

            // 3. Отправляем уведомление
            emailService.sendNotification(event.customerEmail(), subject, model, "order-confirmation.ftlh");

            log.info("Уведомление [{}] отправлено для заказа #{}", event.status(), event.orderId());
        } catch (Exception e) {
            log.error("Ошибка при обработке статуса {}: {}", event.status(), e.getMessage());
            throw e;
        }
    }
}
