package com.inovexx.auth_service.repository;

import com.inovexx.auth_service.entity.UserAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserAuth, Long> {

    Optional<UserAuth> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}