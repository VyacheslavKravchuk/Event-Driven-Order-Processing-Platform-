package com.inovexx.api_gateway.security;

import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final JwtTokenProvider tokenProvider;

    private static final Logger logger =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        // Указываем базовый класс конфигурации
        super(Config.class);
        this.tokenProvider = tokenProvider;
    }

    public static class Config {
        // Сюда можно добавить параметры, например: private boolean enabled;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // 1. Извлекаем заголовок из входящего запроса
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
            }

            String jwt = authHeader.substring(7);

            try {
                if (!tokenProvider.validateToken(jwt)) {
                    return onError(exchange, "Invalid JWT token", HttpStatus.FORBIDDEN);
                }

                Claims claims = tokenProvider.extractAllClaims(jwt);
                String username = claims.getSubject();
                Object rolesObj = claims.get("roles");
                String roles = (rolesObj != null) ? rolesObj.toString() : "";

                // 2. Модифицируем запрос ПРАВИЛЬНО для WebFlux
                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                        .headers(httpHeaders -> {
                            // Явно сохраняем Authorization, чтобы Gateway его не вырезал
                            httpHeaders.set(HttpHeaders.AUTHORIZATION, authHeader);
                            // Добавляем дополнительные заголовки
                            httpHeaders.set("X-Auth-User", username);
                            httpHeaders.set("X-Auth-Roles", roles);
                        })
                        .build();

                // 3. Передаем модифицированный exchange дальше по цепочке
                logger.info("Gateway отправляет заголовок: {}", modifiedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                return chain.filter(exchange.mutate().request(modifiedRequest).build());

            } catch (Exception e) {
                logger.error("Ошибка обработки JWT: {}", e.getMessage());
                return onError(exchange, "JWT Token processing error", HttpStatus.FORBIDDEN);
            }
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        exchange.getResponse().setStatusCode(httpStatus);
        // Можно добавить логирование ошибки err здесь
        return exchange.getResponse().setComplete();
    }
}