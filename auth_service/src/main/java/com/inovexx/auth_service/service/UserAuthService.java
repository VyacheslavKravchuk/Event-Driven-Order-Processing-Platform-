package com.inovexx.auth_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.inovexx.auth_service.dto.UserDto;

public interface UserAuthService {

    UserDto registerNewUser(UserDto userDto) throws JsonProcessingException;
}
