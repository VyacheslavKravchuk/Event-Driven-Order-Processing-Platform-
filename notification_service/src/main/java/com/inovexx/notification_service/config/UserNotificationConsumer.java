package com.inovexx.notification_service.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.notification_service.dto.events.RegisteredNotificationEvent;
import com.inovexx.notification_service.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationConsumer {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.created", groupId = "notification-group")
    public void consumeUserRegistrationEvent(RegisteredNotificationEvent event) {
        log.info("Получено событие регистрации пользователя: {}", event.email());

        try {
            // Конвертируем Record в Map для шаблонизатора
            Map<String, Object> model = objectMapper.convertValue(event, new TypeReference<>() {});

            // Добавляем дополнительные данные, которых нет в событии (опционально)
            model.put("supportEmail", "support@inovexx.com");
            model.put("year", OffsetDateTime.now().getYear());

            emailService.sendNotification(event.email(), "Регистрация", model, "registration.ftlh");

            log.info("Уведомление о регистрации отправлено на {}", event.email());
        } catch (Exception e) {
            log.error("Ошибка обработки события регистрации: {}", e.getMessage());
            throw e;
        }
    }
}

