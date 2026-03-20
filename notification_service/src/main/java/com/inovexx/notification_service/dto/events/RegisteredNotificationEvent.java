package com.inovexx.notification_service.dto.events;

import java.time.OffsetDateTime;

public record RegisteredNotificationEvent(
        Long id,              // Понадобится, если нужно сделать ссылку на профиль или БД
        String username,      // Если хотите напомнить логин в письме
        String email,         // Ключевое поле: куда отправляем письмо
        String firstName,     // Для обращения "Привет, Иван!"
        String lastName,      // Для официальных уведомлений
        String role,          // Если приветственное письмо разное для USER и ADMIN
        OffsetDateTime registeredAt // Полезно для логов"
) {}

