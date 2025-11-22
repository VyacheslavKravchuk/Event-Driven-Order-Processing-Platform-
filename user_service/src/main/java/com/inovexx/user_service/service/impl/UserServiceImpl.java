package com.inovexx.user_service.service.impl;

import com.inovexx.user_service.entity.User;
import com.inovexx.user_service.entity.UserDto;
import com.inovexx.user_service.exception.UserNotFoundException;
import com.inovexx.user_service.mapper.UserMapper;
import com.inovexx.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final WebClient webClient;
    private final UserMapper userMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${auth.service.url}")
    private String authServiceUrl;

    @Override
    public UserDto findUserByUsername(String username) {
        logger.info("Поиск записи о пользователе по username: {}", username);
        return webClient.get()
                .uri(authServiceUrl + "/users/username/" + username)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                    logger.error("User not found with username: {}", username);
                    return Mono.error(new UserNotFoundException("User not found with username: " + username));
                })
                .bodyToMono(User.class)
                .map(userMapper::userToUserDto)
                .block(); // Ждем результат
    }

    @Override
    public UserDto findById(Long id) {
        logger.info("Поиск записи о пользователе по ID: {}", id);
        return webClient.get()
                .uri(authServiceUrl + "/users/" + id)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                    logger.error("User not found with id: {}", id);
                    return Mono.error(new UserNotFoundException("User not found with id: " + id));
                })
                .bodyToMono(User.class)
                .map(userMapper::userToUserDto)
                .block();
    }

    // Получение всех пользователей.
    @Override
    public List<UserDto> findAll() {
        logger.info("Получение всех записей пользователей");
        User[] usersArray = webClient.get()
                .uri(authServiceUrl + "/users")
                .retrieve()
                .onStatus(HttpStatusCode::is2xxSuccessful, response -> Mono.empty())
                .bodyToMono(User[].class)
                .block(); // Ждем результат

        List<User> users = Arrays.asList(Objects.requireNonNull(usersArray));
        List<UserDto> userDtos = users.stream()
                .map(userMapper::userToUserDto)
                .collect(Collectors.toList());

        logger.info("Найдено {} записей пользователей", userDtos.size());
        return userDtos;
    }


}