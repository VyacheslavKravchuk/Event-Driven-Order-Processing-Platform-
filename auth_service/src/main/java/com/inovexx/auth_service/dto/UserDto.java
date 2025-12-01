package com.inovexx.auth_service.dto;

import com.inovexx.auth_service.enums.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

public record UserDto (

        @NotBlank(message = "Имя пользователя не может быть пустым")
        @Size(min = 3, max = 20, message = "Имя пользователя должно быть от 3 до 20 символов")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Имя пользователя может содержать только буквы, цифры и подчеркивание")
        String username,

        @NotBlank(message = "Пароль не может быть пустым")
        @Size(min = 8, message = "Пароль должен содержать не менее 8 символов")
        String password,

        @NotBlank(message = "Email не может быть пустым")
        @Email(message = "Неверный формат Email")
        @Pattern(regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", message = "Неверный формат Email")
        String email,

        @NotBlank(message = "Имя не может быть пустым")
        @Size(max = 50, message = "Имя не может превышать 50 символов")
        String firstName,

        @NotBlank(message = "Фамилия не может быть пустой")
        @Size(max = 50, message = "Фамилия не может превышать 50 символов")
        String lastName,

        @NotNull(message = "Роль не может быть пустой")
        Role role
) {}