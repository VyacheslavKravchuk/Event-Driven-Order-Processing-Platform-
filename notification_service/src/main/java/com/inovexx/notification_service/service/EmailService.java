package com.inovexx.notification_service.service;

import java.util.Map;

public interface EmailService {

    void sendNotification(String userEmail, String subject, Map<String, Object> model, String templateName);
}
