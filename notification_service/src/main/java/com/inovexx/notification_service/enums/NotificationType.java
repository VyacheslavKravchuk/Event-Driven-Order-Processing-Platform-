package com.inovexx.notification_service.enums;

import lombok.Getter;

@Getter
public enum NotificationType {
    REGISTRATION("registration.ftlh"),
    //PASSWORD_UPDATE("password-update.ftlh"),

    // Все статусы заказа теперь используют ОДИН И ТОТ ЖЕ файл
    ORDER_NEW("order-confirmation.ftlh"),
    ORDER_RESERVED("order-confirmation.ftlh"),
    ORDER_PAID("order-confirmation.ftlh"),
    ORDER_SHIPPED("order-confirmation.ftlh"),
    ORDER_COMPLETED("order-confirmation.ftlh"),
    ORDER_CANCELLED("order-confirmation.ftlh");

    private final String templateName;

    NotificationType(String templateName) {
        this.templateName = templateName;
    }
}
