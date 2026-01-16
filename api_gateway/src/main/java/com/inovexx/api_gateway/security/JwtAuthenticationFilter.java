package com.inovexx.api_gateway.security;

import io.jsonwebtoken.Claims;
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
            // 1. Извлекаем заголовок Authorization
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Missing or invalid Authorization header", HttpStatus.UNAUTHORIZED);
            }

            String jwt = authHeader.substring(7);

            try {
                // 2. Валидация токена
                if (!tokenProvider.validateToken(jwt)) {
                    return onError(exchange, "Invalid JWT token", HttpStatus.FORBIDDEN);
                }

                // 3. Извлечение данных (Claims)
                Claims claims = tokenProvider.extractAllClaims(jwt);
                String username = claims.getSubject();
                // Извлекаем роли (преобразуем в строку, если они хранятся как список)
                Object rolesObj = claims.get("roles");
                String roles = (rolesObj != null) ? rolesObj.toString() : "";

                // 4. Пробрасываем данные в заголовках для нижестоящих микросервисов
                ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                        .header("X-Auth-User", username)
                        .header("X-Auth-Roles", roles)
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());

            } catch (Exception e) {
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