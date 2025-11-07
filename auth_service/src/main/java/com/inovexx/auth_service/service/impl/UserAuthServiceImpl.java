package com.inovexx.auth_service.service.impl;


import com.inovexx.auth_service.dto.UserDto;
import com.inovexx.auth_service.entity.UserAuth;
import com.inovexx.auth_service.repository.UserRepository;
import com.inovexx.auth_service.service.UserAuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserAuthServiceImpl implements UserAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
    private static final String USERNAME_REGEX = "^[a-zA-Z0-9_]{3,20}$";

    public UserAuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void registerNewUser(UserDto userDto) {

        if (userRepository.existsByUsername(userDto.getUsername())) {
            throw new IllegalArgumentException("Имя пользователя уже существует.");
        }

        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new IllegalArgumentException("Email уже существует.");
        }

        UserAuth userAuth = new UserAuth();
        userAuth.setUsername(userDto.getUsername());
        userAuth.setPassword(passwordEncoder.encode(userDto.getPassword()));
        userAuth.setEmail(userDto.getEmail());
        userAuth.setFirstName(userDto.getFirstName());
        userAuth.setLastName(userDto.getLastName());
        userAuth.setRole(userDto.getRole());

        userRepository.save(userAuth);
    }
}