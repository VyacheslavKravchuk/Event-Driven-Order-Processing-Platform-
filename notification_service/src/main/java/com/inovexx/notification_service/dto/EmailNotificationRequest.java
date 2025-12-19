package com.inovexx.notification_service.dto;

public record  EmailNotificationRequest (

        String to,

        String subject,

        String body

 ){}
