package com.inovexx.product_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topic.product-events:product.events}")
    private String productEventsTopic;

    @Bean
    public NewTopic UserTopic() {
        return TopicBuilder.name(productEventsTopic)
                .build();
    }
}