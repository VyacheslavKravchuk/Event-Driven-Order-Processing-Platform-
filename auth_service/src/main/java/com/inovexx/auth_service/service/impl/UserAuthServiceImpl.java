package com.inovexx.auth_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.auth_service.dto.UserDto;
import com.inovexx.auth_service.entity.UserAuth;
import com.inovexx.auth_service.mapper.UserMapper;
import com.inovexx.auth_service.repository.UserRepository;
import com.inovexx.auth_service.service.UserAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAuthServiceImpl implements UserAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private final UserMapper userMapper;

    //private static final String USER_TOPIC = "user-events";

    @Value("${kafka.topic.user-events:user.created}")
    private String userEventsTopicName;

    @Override
    public UserDto registerNewUser(UserDto userDto) throws JsonProcessingException {

        if (userRepository.existsByUsername(userDto.username())) {
            throw new IllegalArgumentException("Имя пользователя уже существует.");
        }

        if (userRepository.existsByEmail(userDto.email())) {
            throw new IllegalArgumentException("Email уже существует.");
        }

        UserAuth userAuth = new UserAuth();
        userAuth.setUsername(userDto.username());
        userAuth.setPassword(passwordEncoder.encode(userDto.password()));
        userAuth.setEmail(userDto.email());
        userAuth.setFirstName(userDto.firstName());
        userAuth.setLastName(userDto.lastName());
        userAuth.setRole(userDto.role());

        UserAuth savedUser = userRepository.save(userAuth);
        String userJson = objectMapper.writeValueAsString(savedUser);
        kafkaTemplate.send(userEventsTopicName, savedUser.getId().toString(), userJson);
        return userMapper.userToUserDto(savedUser);
    }
}