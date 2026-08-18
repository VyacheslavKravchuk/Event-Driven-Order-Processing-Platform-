package com.inovexx.user_service.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.inovexx.user_service.dto.UserDto;
import com.inovexx.user_service.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "API для управления пользователями")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;


    @Operation(summary = "Получение всех пользователей",
            description = "Возвращает список всех пользователей (только для менеджеров и администраторов).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешный запрос",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    array = @ArraySchema(schema = @Schema(implementation = UserDto.class)))),
                    @ApiResponse(responseCode = "401", description = "Не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Нет прав доступа")
            })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<List<UserDto>> getAllUser() {
        logger.info("Запрос на получение всех записей о пользователях");
        List<UserDto> userDtos = userService.findAll();
        logger.info("Получено {} записей о пользователях", userDtos.size());
        return ResponseEntity.ok(userDtos);
    }

    @Operation(summary = "Получение информации о текущем пользователе",
            description = "Возвращает информацию о профиле текущего пользователя.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешный запрос",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = UserDto.class))),
                    @ApiResponse(responseCode = "401", description = "Не авторизован")
            })
    @GetMapping("/me")
    public ResponseEntity<UserDto> getMyProfile(Principal principal) {
        // Principal содержит имя пользователя (username) из JWT-токена
        String username = principal.getName();
        UserDto user = userService.findUserByUsername(username);
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Получение пользователя по ID",
            description = "Возвращает информацию о пользователе по указанному ID (только для администраторов).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешный запрос",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = UserDto.class))),
                    @ApiResponse(responseCode = "401", description = "Не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Нет прав доступа"),
                    @ApiResponse(responseCode = "404", description = "Пользователь не найден")
            })
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @Operation(summary = "Изменение информации о пользователе",
            description = "Позволяет изменить информацию о профиле текущего пользователя.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Обновленная информация о пользователе",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserDto.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешное обновление",
                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = UserDto.class))),
                    @ApiResponse(responseCode = "400", description = "Некорректный запрос"),
                    @ApiResponse(responseCode = "401", description = "Не авторизован")
            },
            tags = "Users"
    )
    @PatchMapping("/update")
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<UserDto> updateUser(@Valid @RequestBody UserDto userDto, Principal principal) throws JsonProcessingException {
        logger.info("Запрос обновления пользователя");
        String username = principal.getName();
        return ResponseEntity.ok(userService.updateUser(userDto, username));
    }

}
