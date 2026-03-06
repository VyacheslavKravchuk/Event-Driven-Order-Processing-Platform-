package com.inovexx.order_service.config;

import com.inovexx.order_service.events.OutboxEvent;
import com.inovexx.order_service.repository.OutboxRepository;// Наш новый сервис
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxScheduler {
    private final OutboxRepository outboxRepository;
    private final OutboxProcessor outboxProcessor; // Внедряем процессор
    private final MeterRegistry meterRegistry;
    private Counter processedCounter;
    private Counter errorCounter;
    private Timer processTimer;
    private final AtomicLong pendingEventsGauge = new AtomicLong(0);
    private static final int BATCH_SIZE = 50;
    @PostConstruct
    public void initMetrics() {
        processedCounter = Counter.builder("outbox.events.processed.total").register(meterRegistry);
        errorCounter = Counter.builder("outbox.events.failed.total").register(meterRegistry);
        processTimer = Timer.builder("outbox.events.process.duration").register(meterRegistry);

        Gauge.builder("outbox.events.pending.count", pendingEventsGauge, AtomicLong::get)
                .register(meterRegistry);
    }
    @Scheduled(fixedDelayString = "${app.outbox.interval:5000}")
    public void processOutbox() {
        // Обновляем метрику очереди
        pendingEventsGauge.set(outboxRepository.countByProcessedFalse());
        // Получаем список только ID или объектов (но обрабатываем через прокси)
        List<OutboxEvent> events = outboxRepository.findEventsToProcess(
                OffsetDateTime.now(),
                PageRequest.of(0, BATCH_SIZE)
        );
        if (events.isEmpty()) return;
        for (OutboxEvent event : events) {
            // Теперь вызов идет через внедренный бин outboxProcessor,
            // что позволяет Spring применить @Transactional
            outboxProcessor.processSingleEvent(event.getId(), processedCounter, errorCounter, processTimer);
        }
    }
}
