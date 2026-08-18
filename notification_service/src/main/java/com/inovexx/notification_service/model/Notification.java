package com.inovexx.notification_service.model;

import com.inovexx.notification_service.enums.NotificationStatus;
import com.inovexx.notification_service.enums.NotificationType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;

@Document(collection = "notifications")
@Data
public class Notification {

    @Id
    private String id;
    private String kafkaMessageId; // ID из заголовка Kafka для исключения дублей
    private String recipient;
    private String subject;
    private String content;
    private NotificationType type; // REGISTRATION, ORDER_CREATED
    private NotificationStatus status; // PENDING, SENT, FAILED
    private String errorMessage;    // Текст ошибки, если не ушло
    private int retryCount;        // Сколько раз пытались переотправить
    private OffsetDateTime createdAt;
    private OffsetDateTime sentAt;
}
