package com.inovexx.notification_service.config;

import com.inovexx.notification_service.config.send.EmailService;
import com.inovexx.notification_service.events.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    // Внедряем сервисы для отправки конкретных типов уведомлений
    private final EmailService emailService;
    // Предполагаем, что у вас есть аналогичные SmsService и PushNotificationService
    // private final SmsService smsService;
    // private final PushNotificationService pushNotificationService;

    @KafkaListener(topics = "order.events", groupId = "notification-service-group")
    public void handleOrderCompletedEvent(OrderCompletedEvent event) {
        log.info("Received OrderCompletedEvent for order ID: {}", event.orderId());

        // Формируем сообщение для пользователя
        String subject = "Ваш заказ №" + event.orderId() + " завершен";
        String body = String.format(
                "Здравствуйте, %s! Ваш заказ на сумму %s рублей успешно завершен. Спасибо за покупку!",
                event.username(), event.totalAmount()
        );

        // Отправляем уведомления с использованием соответствующих сервисов
        try {
            // Отправка Email
            emailService.sendSimpleMessage(event.userEmail(), subject, body);
            log.info("Email notification sent to {}", event.userEmail());

            // Здесь можно добавить логику для отправки SMS и Push-уведомлений
            // if (user.isSmsEnabled()) {
            //     smsService.sendSms(user.getPhoneNumber(), body);
            // }
            // if (user.isPushEnabled()) {
            //     pushNotificationService.sendPushNotification(user.getDeviceToken(), subject, body);
            // }

        } catch (Exception e) {
            log.error("Failed to send notification for order ID {}: {}",
                    event.orderId(), e.getMessage());
            // Можно реализовать логику повторной отправки или запись в отдельный топик ошибок
        }
    }
}
