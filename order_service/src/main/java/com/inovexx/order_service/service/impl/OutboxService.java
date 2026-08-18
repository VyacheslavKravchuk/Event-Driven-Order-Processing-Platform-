package com.inovexx.order_service.service.impl;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.order_service.enums.EventStatus;
import com.inovexx.order_service.enums.OutboxEventType;
import com.inovexx.order_service.events.OutboxEvent;
import com.inovexx.order_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;


    /**
     * Propagation.MANDATORY гарантирует, что событие не будет сохранено
     * вне транзакции основного бизнес-процесса (заказа).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void saveEvent(UUID orderId, OutboxEventType eventType, Object payload) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);

            OutboxEvent event = new OutboxEvent();
            event.setOrderId(orderId);
            event.setEventType(eventType);
            event.setPayload(jsonPayload);

            // ЯВНО УСТАНАВЛИВАЕМ СТАТУС ОБРАБОТКИ
            event.setProcessed(false);

            event.setStatus(EventStatus.PENDING);
            event.setRetryCount(0);
            event.setCreatedAt(OffsetDateTime.now());
            event.setNextAttemptAt(OffsetDateTime.now());

            outboxRepository.save(event);

            log.debug("Событие {} сохранено", eventType);
        } catch (Exception e) {
            log.error("Ошибка сохранения в Outbox: {}", e.getMessage());
            throw new RuntimeException("Critical outbox error", e);
        }
    }
}
