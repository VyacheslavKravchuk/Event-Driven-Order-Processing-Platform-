package com.inovexx.auth_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topic.user-events:user.created}")
    private String userEventsTopicName;

    @Bean
    public NewTopic UserTopic() {
        return TopicBuilder.name(userEventsTopicName)
                .build();
    }
}