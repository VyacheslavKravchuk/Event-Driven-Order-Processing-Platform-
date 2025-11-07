package com.inovexx.auth_service.service;

import com.inovexx.auth_service.dto.UserDto;

public interface UserAuthService {

    void registerNewUser(UserDto userDto);
}
