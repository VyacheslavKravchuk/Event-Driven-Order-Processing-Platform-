package com.inovexx.notification_service.dto;


public record PushNotificationRequest (

        String token,     // Токен конкретного устройства (Android/iOS)

        String title,     // Заголовок уведомления

        String message,   // Тело сообщения

        String topic      // Опционально: для рассылки по темам
 ){}
