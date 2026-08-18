package com.inovexx.order_service.service.outbox;

import com.inovexx.order_service.enums.EventStatus;
import com.inovexx.order_service.events.OutboxEvent;
import com.inovexx.order_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.Optional;
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxFailureService {
    private static final int MAX_RETRIES = 5;
    private static final long BASE_DELAY_SECONDS = 30L;
    private static final long MAX_DELAY_SECONDS = 3600L;
    private final OutboxRepository outboxRepository;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(Long eventId, Exception exception) {
        OutboxEvent event = outboxRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + eventId));
        int currentRetry = Optional.ofNullable(event.getRetryCount()).orElse(0);
        int nextRetry = currentRetry + 1;
        event.setRetryCount(nextRetry);
        event.setLastErrorMessage(truncate(exception.getMessage(), 1000));
        event.setLastAttemptAt(OffsetDateTime.now());
        if (nextRetry >= MAX_RETRIES) {
            event.setStatus(EventStatus.FAILED);
            event.setProcessed(true);
            event.setNextAttemptAt(null);
            log.error(
                    "Outbox-{}: превышено число попыток ({}), статус переведён в FAILED",
                    eventId,
                    nextRetry
            );
        } else {
            long delaySeconds = calculateDelaySeconds(nextRetry);
            event.setStatus(EventStatus.RETRYING);
            event.setProcessed(false);
            event.setNextAttemptAt(OffsetDateTime.now().plusSeconds(delaySeconds));
            log.warn(
                    "Outbox-{}: попытка {} завершилась ошибкой. Повтор через {} сек.",
                    eventId,
                    nextRetry,
                    delaySeconds
            );
        }
        outboxRepository.save(event);
    }
    private long calculateDelaySeconds(int attempt) {
        long delay = (long) Math.pow(2, attempt - 1) * BASE_DELAY_SECONDS;
        return Math.min(delay, MAX_DELAY_SECONDS);
    }
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
