package com.inovexx.order_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topic.order-events:order.events}")
    private String orderEventsTopicName;

    @Bean
    public NewTopic orderTopic() {
        return TopicBuilder.name(orderEventsTopicName)
                // 3 партиции позволяют распараллелить обработку на 3 потребителя
                .partitions(3)
                // Фактор репликации 3 гарантирует, что данные не пропадут при отказе 2 брокеров
                .replicas(3)
                // Настройка минимального количества синхронных реплик на уровне топика
                .configs(java.util.Map.of(
                        "min.insync.replicas", "2",
                        "retention.ms", "604800000" // Хранить события 7 дней
                ))
                .build();
    }
}
