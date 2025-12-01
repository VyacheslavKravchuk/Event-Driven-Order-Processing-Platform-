package com.inovexx.user_service.repository;

import com.inovexx.user_service.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUserId(Long id);

    Optional<User> findByEmail(@NotBlank(message = "Email cannot be blank")
                               @Email(message = "Invalid email format") String email);
}