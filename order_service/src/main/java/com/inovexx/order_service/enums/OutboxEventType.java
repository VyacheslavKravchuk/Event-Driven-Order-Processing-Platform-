package com.inovexx.order_service.enums;

public enum OutboxEventType {

    START_GRPC_SAGA,
    ORDER_CREATED,
    ORDER_PAID,
    ORDER_STATUS_UPDATED,
    START_CANCEL_SAGA,
    ORDER_CANCELLED,
    ORDER_COMPLETED
}
