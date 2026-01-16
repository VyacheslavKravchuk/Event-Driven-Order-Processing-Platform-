package com.inovexx.api_gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity // ВАЖНО: Используем реактивную безопасность
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        logger.info("Настройка цепочки фильтров безопасности для API Gateway (Reactive)...");

        return http
                // 1. Отключаем CSRF, так как работаем с JWT
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // 2. Отключаем форму логина и базовую аутентификацию (они не нужны в шлюзе)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                // 3. Настраиваем правила доступа
                .authorizeExchange(exchanges -> exchanges
                        // Разрешаем доступ к Swagger, документации и мониторингу
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**", "/actuator/**").permitAll()
                        // Все остальные запросы должны проходить через шлюз
                        // Важно: в шлюзе проверку JWT обычно делает JwtAuthenticationFilter (GatewayFilter),
                        // поэтому здесь мы можем разрешить всё ("anyExchange().permitAll()"),
                        // чтобы шлюз просто проксировал запросы к микросервисам.
                        .anyExchange().permitAll()
                )
                .build();
    }
}

