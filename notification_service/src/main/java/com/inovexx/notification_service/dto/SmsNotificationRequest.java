package com.inovexx.notification_service.dto;

public record SmsNotificationRequest (

        String phoneNumber,

        String message
 ){}
