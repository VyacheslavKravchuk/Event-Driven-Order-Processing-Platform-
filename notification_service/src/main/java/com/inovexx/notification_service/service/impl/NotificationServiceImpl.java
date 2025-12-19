package com.inovexx.notification_service.service.impl;

import com.inovexx.notification_service.redis.RateLimiterService;
import com.inovexx.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final RateLimiterService rateLimiterService;


    @Override
    public ResponseEntity<String> sendNotification(@PathVariable String userId) {

        // --- АКТИВАЦИЯ ЛОГИКИ ОГРАНИЧЕНИЯ ЗДЕСЬ ---
        if (!rateLimiterService.allowRequest(userId)) {
            // Если лимит превышен, возвращаем ошибку 429 Too Many Requests
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Превышен лимит запросов. Попробуйте позже.");
        }
        // --- АКТИВАЦИЯ ЛОГИКИ ОГРАНИЧЕНИЯ ЗДЕСЬ ---

        // Основная логика отправки уведомления, если проверка пройдена
        // notificationService.sendEmail(userId, ...);
        return ResponseEntity.ok("Уведомление отправлено успешно.");
    }
}
