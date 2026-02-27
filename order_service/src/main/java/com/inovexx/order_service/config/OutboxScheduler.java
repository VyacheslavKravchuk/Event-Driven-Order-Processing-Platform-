package com.inovexx.order_service.config;

import com.inovexx.order_service.events.OutboxEvent;
import com.inovexx.order_service.repository.OutboxRepository;
import com.inovexx.order_service.service.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final KafkaProducerService kafkaProducerService;

    @Value("${kafka.topic.order-events}")
    private String topic;

    private static final int MAX_RETRY_ATTEMPTS = 5; // Максимум 5 попыток

    @Scheduled(fixedDelay = 5000) // Проверка каждые 5 секунд
    @Transactional
    public void processOutbox() {
        // Ищем записи, которые еще не обработаны и время попытки которых пришло
        List<OutboxEvent> events = outboxRepository
                .findByProcessedFalseAndNextAttemptAtBefore(LocalDateTime.now());

        if (events.isEmpty()) return;

        for (OutboxEvent event : events) {
            try {
                // Отправка в Kafka
                kafkaProducerService.sendMessage(topic, event.getPayload());

                // Успех: помечаем как обработанное
                event.setProcessed(true);
                outboxRepository.save(event);
                log.info("Событие Order ID: {} успешно отправлено", event.getOrderId());

            } catch (Exception e) {
                handleFailedAttempt(event, e);
            }
        }
    }

    private void handleFailedAttempt(OutboxEvent event, Exception e) {
        int nextRetryCount = event.getRetryCount() + 1;
        log.error("Ошибка отправки события {}. Попытка: {}/{}",
                event.getOrderId(), nextRetryCount, MAX_RETRY_ATTEMPTS);

        if (nextRetryCount >= MAX_RETRY_ATTEMPTS) {
            // Если попытки исчерпаны, можно пометить сообщение как ERROR или оставить для ручного разбора
            event.setProcessed(true); // Больше не пытаемся, чтобы не зацикливаться
            log.error("Событие {} переведено в статус ERROR после {} попыток", event.getOrderId(), MAX_RETRY_ATTEMPTS);
        } else {
            event.setRetryCount(nextRetryCount);
            // Экспоненциальная задержка: 20с, 40с, 80с...
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(20L * nextRetryCount));
        }

        outboxRepository.save(event);
    }
}
