package com.inovexx.user_service.service;

import com.inovexx.user_service.entity.UserDto;
import java.util.List;

public interface UserService {

    UserDto findUserByUsername(String username);

    UserDto findById(Long id);

    List<UserDto> findAll();

    UserDto updateUser(UserDto userDtoNew, String username) throws JsonProcessingException;
}
