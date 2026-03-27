package com.inovexx.notification_service.enums;

import lombok.Getter;

@Getter
public enum NotificationType {

    // Каждому типу назначаем имя файла шаблона
    REGISTRATION("registration.ftlh"),
    PASSWORD_UPDATE("password-update.ftlh"),
    ORDER_NEW("order-new.ftlh"),
    ORDER_RESERVED("order-reserved.ftlh"),
    ORDER_PAID("order-paid.ftlh"),
    ORDER_SHIPPED("order-shipped.ftlh"),
    ORDER_COMPLETED("order-completed.ftlh"),
    ORDER_CANCELLED("order-cancelled.ftlh");

    // Тот самый метод, который вызывается в сервисе
    private final String templateName;

    // Конструктор enum
    NotificationType(String templateName) {
        this.templateName = templateName;
    }

}
