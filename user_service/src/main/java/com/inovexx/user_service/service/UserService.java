package com.inovexx.user_service.service;

import com.inovexx.user_service.entity.UserDto;

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


}
