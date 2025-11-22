package com.inovexx.user_service.entity;



import com.inovexx.user_service.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

/**
 * Класс сущности пользователя
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class User {

    private Long id;

    @NotBlank(message = "Username не может быть пустым")
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank(message = "Password не может быть пустым")
    private String password;

    @NotBlank(message = "Email не может быть пустым")
    @Email(message = "Некорректный формат email")
    private String email;

    private String firstName;

    private String lastName;

    private Role role;


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(username, user.username) && Objects.equals(password, user.password) && Objects.equals(email, user.email) && Objects.equals(firstName, user.firstName) && Objects.equals(lastName, user.lastName) && role == user.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username, password, email, firstName, lastName, role);
    }
}
