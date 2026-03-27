package com.inovexx.notification_service.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.notification_service.dto.events.OrderNotificationEvent;
import com.inovexx.notification_service.dto.events.RegisteredNotificationEvent;
import com.inovexx.notification_service.enums.NotificationType;
import com.inovexx.notification_service.service.EmailService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import org.springframework.mail.MailException;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationConsumer {
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final ProxyManager<String> buckets;
    // Считываем настройки Rate Limit из application.properties
    @Value("${app.notification.rate-limit.capacity:5}")
    private long rateLimitCapacity;
    @Value("${app.notification.rate-limit.duration-minutes:10}")
    private int rateLimitDuration;
    @Value("${app.notification.support-email:support@inovexx.com}")
    private String supportEmail;
    /**
     * Регистрация пользователя.
     * Используем delayExpression для динамической настройки задержки.
     */
    @RetryableTopic(
            attempts = "${app.notification.retry.attempts:3}",
            backoff = @Backoff(delayExpression = "${app.notification.retry.delay-ms:300000}"),
            include = {MailException.class, RuntimeException.class}
    )
    @KafkaListener(topics = "user.created", groupId = "notification-group")
    public void consumeUserRegistrationEvent(RegisteredNotificationEvent event) {
        processEvent(event.email(), "Регистрация прошла успешно!", event, NotificationType.REGISTRATION);
    }
    /**
     * События заказа.
     */
    @RetryableTopic(
            attempts = "${app.notification.retry.attempts:3}",
            backoff = @Backoff(delayExpression = "${app.notification.retry.delay-ms:300000}"),
            include = {MailException.class, RuntimeException.class}
    )
    @KafkaListener(topics = "order.events", groupId = "notification-group")
    public void consumeOrderEvent(OrderNotificationEvent event) {
        if ("IN_PROGRESS".equals(event.status())) return;
        try {
            NotificationType type = resolveType(event.status());
            processEvent(event.customerEmail(), getOrderSubject(event.status(), event.orderId()), event, type);
        } catch (IllegalArgumentException e) {
            log.error("Пропуск события заказа {}: {}", event.orderId(), e.getMessage());
        }
    }
    private void processEvent(String email, String subject, Object eventDto, NotificationType type) {
        // Ключ для Redis с префиксом для порядка
        String bucketKey = "rate-limit:email:" + email.toLowerCase();
        Bucket bucket = buckets.builder().build(bucketKey, this::getBucketConfiguration);
        if (bucket.tryConsume(1)) {
            try {
                Map<String, Object> model = objectMapper.convertValue(eventDto, new TypeReference<>() {});
                model.put("supportEmail", supportEmail);

                emailService.sendNotification(email, subject, model, type);
                log.info("Уведомление [{}] отправлено на {}", type, email);
            } catch (Exception e) {
                log.error("Ошибка при отправке [{}]: {}", type, e.getMessage());
                throw e;
            }
        } else {
            log.warn("Rate limit достигнут для {}. Событие [{}] сохранено как пропущенное.", email, type);
            emailService.saveSkippedNotification(email, subject, type, "Rate limit exceeded");
        }
    }
    /**
     * Теперь конфигурация использует значения из properties
     */
    private BucketConfiguration getBucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rateLimitCapacity)
                        .refillIntervally(rateLimitCapacity, Duration.ofMinutes(rateLimitDuration))
                        .build())
                .build();
    }
    private NotificationType resolveType(String status) {
        return switch (status) {
            case "NEW"       -> NotificationType.ORDER_NEW;
            case "RESERVED"  -> NotificationType.ORDER_RESERVED;
            case "PAID"      -> NotificationType.ORDER_PAID;
            case "SHIPPED"   -> NotificationType.ORDER_SHIPPED;
            case "COMPLETED" -> NotificationType.ORDER_COMPLETED;
            case "CANCELLED" -> NotificationType.ORDER_CANCELLED;
            default -> throw new IllegalArgumentException("Неподдерживаемый статус: " + status);
        };
    }
    private String getOrderSubject(String status, String orderId) {
        return switch (status) {
            case "NEW"       -> "Ваш заказ #" + orderId + " принят";
            case "RESERVED"  -> "Товары забронированы #" + orderId;
            case "PAID"      -> "Оплата получена #" + orderId;
            case "SHIPPED"   -> "Заказ #" + orderId + " в пути";
            case "COMPLETED" -> "Заказ #" + orderId + " доставлен";
            case "CANCELLED" -> "Заказ #" + orderId + " отменен";
            default          -> "Обновление по заказу #" + orderId;
        };
    }
    @DltHandler
    public void handleDlt(Object event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Событие в DLT (топик {}): {}", topic, event);
    }
}

