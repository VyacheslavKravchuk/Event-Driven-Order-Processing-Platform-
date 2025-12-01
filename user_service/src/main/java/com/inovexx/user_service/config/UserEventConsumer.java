package com.inovexx.user_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.user_service.entity.User;
import com.inovexx.user_service.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserEventConsumer {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(UserEventConsumer.class);

    public UserEventConsumer(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "user-events", groupId = "user-profile-group")
    public void consumeUserRegistrationEvent(String message) {
        try {
            User user = objectMapper.readValue(message, User.class);

            if (!userRepository.existsById(user.getUserId())) {
                userRepository.save(user);
                System.out.println("Создан новый профиль пользователя с ID: " + user.getUserId());
                logger.info("Создан новый профиль пользователя с ID: {}", user.getUserId());
            } else {
                logger.error("Профиль пользователя с ID {} уже существует.", user.getUserId());
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке сообщения из Kafka: {}", message, e);
        }
    }
}
