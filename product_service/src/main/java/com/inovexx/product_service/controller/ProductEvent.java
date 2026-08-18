package com.inovexx.product_service.dto;

import java.time.Instant;

public record ProductEvent(
        String productId,      // ID из Mongo
        String eventType,      // CREATED, UPDATED, DELETED
        String name,           // Чтобы склад понимал, что это за товар в логах/отчетах
        Instant timestamp      // Когда это произошло
) {}