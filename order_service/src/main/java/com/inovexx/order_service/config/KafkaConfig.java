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
    public NewTopic OrderTopic() {
        return TopicBuilder.name(orderEventsTopicName)
                .build();
    }
}
