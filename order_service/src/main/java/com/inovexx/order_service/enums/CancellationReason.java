package com.inovexx.order_service.enums;

public enum CancellationReason {

    USER_REQUEST,
    DUPLICATE_REQUEST,
    PAYMENT_FAILED,
    INSUFFICIENT_STOCK,
    TECHNICAL_FAILURE,
    INVENTORY_COMMIT_FAILED,
    SHIPMENT_ISSUE
}
