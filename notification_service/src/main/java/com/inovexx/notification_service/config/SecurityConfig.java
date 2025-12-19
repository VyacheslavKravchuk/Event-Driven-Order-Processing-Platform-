package com.inovexx.notification_service.config;

import com.inovexx.notification_service.security.JwtAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Активирует работу @PreAuthorize
//@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        logger.info("Настройка цепочки фильтров безопасности...");

        http
                // Отключаем CSRF, так как используем JWT.  Это важно для API, которые не используют сессии.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        // Настраиваем, чтобы Spring не использовал сессии (JWT stateless).  Каждый запрос должен содержать JWT.
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // Настраиваем правила доступа к URL.
                .authorizeHttpRequests(authorize -> authorize
                        // Разрешаем доступ к Swagger и actuator без аутентификации.  Полезно для документации и мониторинга.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/**").permitAll()
                        // Все остальные запросы должны быть аутентифицированы.  Это означает, что для доступа к ним требуется валидный JWT.
                        .anyRequest().authenticated()
                );

        // Добавляем наш JWT фильтр перед стандартным фильтром аутентификации по логину/паролю.
        // Это гарантирует, что JWT будет проверен перед любой другой аутентификацией.
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        logger.info("Цепочка фильтров безопасности настроена.");
        return http.build();
    }
}
