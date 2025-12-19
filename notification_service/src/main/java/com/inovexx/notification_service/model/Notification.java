package com.inovexx.notification_service.model;

import com.inovexx.notification_service.enums.NotificationType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notifications")
@Data
public class Notification {

    @Id
    private String id;

    private String recipient;
    private String subject;
    private String content;
    private NotificationType type;
    private LocalDateTime createdAt;
    private boolean sent;
    private LocalDateTime sentAt;
}
