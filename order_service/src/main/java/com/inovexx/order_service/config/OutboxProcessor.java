package com.inovexx.order_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.order_service.events.OutboxEvent;
import com.inovexx.order_service.repository.OutboxRepository;
import com.inovexx.order_service.service.impl.OrderServiceImpl;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;

import static com.inovexx.order_service.enums.EventStatus.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxProcessor {
    private final OutboxRepository outboxRepository;
    private final OrderServiceImpl orderService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private static final int MAX_RETRIES = 5;
    /**
     * REQUIRES_NEW гарантирует, что каждое событие фиксируется независимо.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleEvent(Long eventId, Counter processedCounter, Counter errorCounter, Timer processTimer) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // 1. SELECT FOR UPDATE (Блокировка строки в БД)
            OutboxEvent event = outboxRepository.findByIdForProcessing(eventId)
                    .orElse(null);
            if (event == null) {
                log.debug("Outbox-{}: уже обработано или занято другим воркером", eventId);
                return;
            }
            log.info("Outbox-{}: обработка {} (попытка {})", eventId, event.getEventType(), event.getRetryCount());

            Long orderId = event.getOrderId(); // Берем из поля сущности, а не из JSON
            if (orderId == null) {
                throw new IllegalStateException("OrderId is missing in OutboxEvent entity!");
            }
            dispatchEvent(event, orderId);

            // 3. Фиксация успеха
            event.setProcessed(true);
            event.setStatus(SUCCESS);
            outboxRepository.save(event);

            processedCounter.increment();
        } catch (Exception e) {
            handleFailure(eventId, e, errorCounter);
        } finally {
            sample.stop(processTimer);
        }
    }
    private void dispatchEvent(OutboxEvent event, Long orderId) {
        switch (event.getEventType()) {
            case "START_GRPC_SAGA":
                orderService.processOrderSaga(orderId);
                break;

            case "ORDER_PAID":
                // Просто логируем. Ошибки не будет, транзакция закоммитится успешно.
                log.info("EVENT-LOG: Order {} status changed to PAID. No action required for now.", orderId);
                break;
            default:
                // Важно: не бросаем Exception для типов, которые нам не критичны.
                // Просто помечаем их как обработанные "заглушкой".
                log.warn("Unknown or unhandled event type: {}. Skipping.", event.getEventType());
        }
    }

    private void handleFailure(Long eventId, Exception e, Counter errorCounter) {
        OutboxEvent event = outboxRepository.findById(eventId).orElseThrow();
        int attempt = event.getRetryCount() + 1;
        errorCounter.increment();
        log.error("Outbox-{}: ошибка на попытке {}: {}", eventId, attempt, e.getMessage());
        event.setRetryCount(attempt);
        event.setStatus(RETRYING);
        if (attempt >= MAX_RETRIES) {
            event.setStatus(FAILED);
            event.setProcessed(true);
            log.error("Outbox-{}: перемещено в DLQ/FAILED", eventId);
        } else {
            long delay = Math.min((long) Math.pow(2, attempt - 1) * 30, 3600);
            event.setNextAttemptAt(OffsetDateTime.now().plusSeconds(delay));
        }
        outboxRepository.save(event);
    }
}
