package com.inovexx.user_service.entity;

import com.inovexx.user_service.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

/**
 * Класс сущности пользователя
 */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@Table(name = "users")
public class User {
    @Id
    private Long userId;

    @Column(unique = true, nullable = false)
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    @Column(unique = true, nullable = false)
    @NotBlank
    @Email(message = "Некорректный формат email")
    private String email;

    @Column(nullable = false, length = 50)
    @NotBlank
    private String firstName;

    @Column(nullable = false, length = 50)
    @NotBlank
    private String lastName;

    @Enumerated(EnumType.STRING)
    private Role role;

    // Контактная информация
    @Column(length = 50)
    @Pattern(regexp = "^\\+?[0-9.()\\-\\s]{6,20}$",
            message = "Некорректный формат номера телефона")
    private String phoneNumber;

    @Column(length = 50)
    private String city;

    @Column(length = 250)
    private String address;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(userId, user.userId) && Objects.equals(username, user.username) && Objects.equals(email, user.email) && Objects.equals(firstName, user.firstName) && Objects.equals(lastName, user.lastName) && role == user.role && Objects.equals(phoneNumber, user.phoneNumber) && Objects.equals(city, user.city) && Objects.equals(address, user.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, username, email, firstName, lastName, role, phoneNumber, city, address);
    }
}
