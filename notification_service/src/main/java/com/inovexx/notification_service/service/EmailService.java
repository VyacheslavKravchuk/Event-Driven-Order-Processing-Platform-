package com.inovexx.notification_service.service;

import com.inovexx.notification_service.enums.NotificationType;

import java.util.Map;

public interface EmailService {

    void sendNotification(String userEmail, String subject,
                          Map<String, Object> model, NotificationType type);

    void saveSkippedNotification(String email, String subject, NotificationType type, String reason);
}
