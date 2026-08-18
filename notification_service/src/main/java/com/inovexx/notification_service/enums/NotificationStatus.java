package com.inovexx.notification_service.enums;

public enum NotificationStatus {
    PENDING,    // Создано, ожидает отправки
    SENT,       // Успешно отправлено
    ERROR,    // Произошла техническая ошибка (будет retry)
    SKIPPED_BY_RATE_LIMIT,
    CANCELED    // Отменено (например, невалидный email)
}
