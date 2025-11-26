package com.inovexx.user_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.user_service.entity.User;
import com.inovexx.user_service.entity.UserDto;
import com.inovexx.user_service.exception.UserAlreadyExistsException;
import com.inovexx.user_service.exception.UserNotFoundException;
import com.inovexx.user_service.mapper.UserMapper;
import com.inovexx.user_service.repository.UserRepository;
import com.inovexx.user_service.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.kafka.KafkaException;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {


    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;

    private final UserMapper userMapper;

    private final ObjectMapper objectMapper;

    private final KafkaTemplate<String, String> kafkaTemplate;


    @Override
    public UserDto findUserByUsername(String username) {
        logger.info("Поиск записи о пользователе по username: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with username: " + username));
        return userMapper.userToUserDto(user);
    }

    @Override
    public UserDto findById(Long id) {
        logger.info("Поиск записи о пользователе по ID: {}", id);
        User user = userRepository.findByUserId(id)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with id: " + id));
        return userMapper.userToUserDto(user);
    }

    @Override
    public List<UserDto> findAll() {
        logger.info("Получение всех записей пользователей");
        List<UserDto> userDtos = userRepository.findAll()
                .stream()
                .map(userMapper::userToUserDto)
                .collect(Collectors.toList());
        logger.info("Найдено {} записей пользователей", userDtos.size());
        return userDtos;
    }

    @Override
    @Transactional
    public UserDto updateUser(UserDto userDtoNew, String currentUsername) throws JsonProcessingException, KafkaException {
        logger.info("Обновление профиля пользователя: {}", currentUsername);

        User user = userRepository.findByUsername(currentUsername)
                .orElseThrow(() ->
                        new UserNotFoundException("Пользователь с таким именем пользователя не найден: " + currentUsername));

        userMapper.updateUserFromDto(userDtoNew, user);

        if (!user.getEmail().equals(userDtoNew.getEmail())) {
            if (userRepository.existsByEmail(userDtoNew.getEmail())) {
                throw new UserAlreadyExistsException("Пользователь с таким email уже существует.");
            }
        }

        if (!user.getUsername().equals(userDtoNew.getUsername())) {
            if (userRepository.existsByUsername(userDtoNew.getUsername())) {
                throw new UserAlreadyExistsException("Пользователь с таким именем пользователя уже существует.");
            }
        }

        userRepository.save(user);

        String userJson = objectMapper.writeValueAsString(user);
        kafkaTemplate.send("user-events", user.getUserId().toString(), userJson); // Используем правильный топик

        logger.info("Сообщение отправлено в Kafka об обновлении пользователя: {}", user.getUsername());

        return userMapper.userToUserDto(user);
    }

}