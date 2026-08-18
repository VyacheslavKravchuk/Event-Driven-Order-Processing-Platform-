package com.inovexx.order_service.dto;

import com.inovexx.order_service.enums.CancellationReason;

public record CancelOrderRequest(
        CancellationReason reason
) {}