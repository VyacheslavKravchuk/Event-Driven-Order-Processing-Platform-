package com.inovexx.auth_service.controller;

import com.inovexx.auth_service.dto.AuthenticationResponse;
import com.inovexx.auth_service.dto.UserDto;
import com.inovexx.auth_service.security.UserDetailsServiceImpl;
import com.inovexx.auth_service.service.UserAuthService;
import com.inovexx.auth_service.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * Класс контроллер для пользователей
 */


@RestController
@RequestMapping("/users_auth")
public class UserAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserAuthService userService;
    private final UserDetailsServiceImpl userDetailsService;
    private static final Logger logger = LoggerFactory.getLogger(UserAuthController.class);


    public UserAuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                          UserAuthService userService, UserDetailsServiceImpl userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.userDetailsService = userDetailsService;
    }

    @Operation(summary = "Регистрация пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь успешно зарегистрирован"),
            @ApiResponse(responseCode = "400", description = "Неверный запрос (например, неверный формат данных)"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody UserDto userDto) {
        logger.info("Запрос регистрации");
        userService.registerNewUser(userDto);
        return ResponseEntity.ok("Пользователь зарегистрирован");
    }

    @Operation(summary = "Аутентификация пользователя и генерация токена")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешная аутентификация, возвращается JWT токен"),
            @ApiResponse(responseCode = "401", description = "Неверные учетные данные"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @PostMapping("/authenticate")
    public ResponseEntity<?> generateToken(@RequestBody UserDto userDto) {
        logger.info("Запрос аутентификации");

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(userDto.getUsername(), userDto.getPassword())
        );

        final UserDetails userDetails = userDetailsService.loadUserByUsername(userDto.getUsername());
        final String jwt = jwtUtil.generateToken(userDetails);

        return ResponseEntity.ok(new AuthenticationResponse(jwt));
    }
}
