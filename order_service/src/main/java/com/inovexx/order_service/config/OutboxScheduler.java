package com.inovexx.order_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.order_service.events.OutboxEvent;
import com.inovexx.order_service.repository.OutboxRepository;
import com.inovexx.order_service.service.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.order-events}")
    private String topic;

    @Scheduled(fixedDelay = 1000) // Проверка каждую секунду
    @Transactional
    public void processOutbox() {
        // Находим все необработанные события
        List<OutboxEvent> events = outboxRepository.findByProcessedFalse();

        for (OutboxEvent event : events) {
            try {
                // Отправляем в Kafka
                kafkaProducerService.sendMessage(topic, event.getPayload());

                // Помечаем как обработанное
                event.setProcessed(true);
                outboxRepository.save(event);

                logger.info("Событие {} успешно отправлено в Kafka", event.getOrderId());
            } catch (Exception e) {
                logger.error("Ошибка при отправке события {}: {}", event.getOrderId(), e.getMessage());
                // Здесь можно реализовать счетчик попыток (retry count)
            }
        }
    }
}

