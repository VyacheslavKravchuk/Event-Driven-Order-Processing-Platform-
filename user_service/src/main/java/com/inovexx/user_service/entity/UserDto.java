package com.inovexx.user_service.entity;

import com.inovexx.user_service.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for {@link User}
 */

@Data
public class UserDto {

    private Long id;

    @NotBlank(message = "Username cannot be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    String username;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email format")
    String email;

    @NotBlank
    String firstName;

    @NotBlank
    String lastName;

    Role role;

    @Pattern(regexp = "^\\+?[0-9.()\\-\\s]{6,20}$",
            message = "Некорректный формат номера телефона")
    private String phoneNumber;

    private String city;

    private String address;

}