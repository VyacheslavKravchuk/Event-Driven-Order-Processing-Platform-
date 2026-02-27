package com.inovexx.user_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.inovexx.user_service.dto.UserDto;

import java.util.List;

public interface UserService {
    /**
     * Получение информации об авторизованном пользователе
     *
     * @return UserDto данный пользователь
     */

    UserDto findUserByUsername(String username);

    UserDto findById(Long id);

    List<UserDto> findAll();


    UserDto updateUser(UserDto userDtoNew, String username) throws JsonProcessingException;
}
