package com.inovexx.api_gateway.config;

import com.inovexx.api_gateway.security.JwtAuthenticationFilter;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.boot.CommandLineRunner;

import java.util.Objects;
import java.util.Optional;

@Configuration
public class GatewayConfiguration {

    @Bean
    public RouteLocator myRoutes(RouteLocatorBuilder builder, JwtAuthenticationFilter authFilter) {
        return builder.routes()
                // 1. SWAGGER ROUTES
                // Оставляем stripPrefix(2), так как обычно микросервис ждет /v3/api-docs без префикса /api/service
                .route("auth_swagger_route", r -> r.path("/api/auth/v3/api-docs").filters(f -> f.stripPrefix(2)).uri("lb://auth-service"))
                .route("user_swagger_route", r -> r.path("/api/users/v3/api-docs").filters(f -> f.stripPrefix(2)).uri("lb://user-service"))
                .route("inventory_swagger_route", r -> r.path("/api/inventory/v3/api-docs").filters(f -> f.stripPrefix(2)).uri("lb://inventory-service"))
                .route("product_swagger_route", r -> r.path("/api/products/v3/api-docs").filters(f -> f.stripPrefix(2)).uri("lb://product-service"))
                .route("order_swagger_route", r -> r.path("/api/orders/v3/api-docs").filters(f -> f.stripPrefix(2)).uri("lb://order-service"))

                // 2. AUTH SERVICE
                .route("auth_route", r -> r.path("/api/auth/**")
                        .filters(f -> f.requestRateLimiter(config -> {
                            config.setRateLimiter(redisRateLimiter());
                            config.setKeyResolver(userKeyResolver());
                        }))
                        .uri("lb://auth-service"))

                // 3. USER SERVICE (Включая Wallets и Transactions)
                .route("user_service_route", r -> r.path(
                                "/api/users/**",
                                "/api/wallets/**",
                                "/api/transactions/**"
                        )
                        .filters(f -> f
                                        .filter(authFilter.apply(new JwtAuthenticationFilter.Config()))
                                        .requestRateLimiter(config -> {
                                            config.setRateLimiter(redisRateLimiter());
                                            config.setKeyResolver(userKeyResolver());
                                        })
                                // УБРАНО: stripPrefix и preserveHostHeader (часто мешает локальному резолвингу)
                        )
                        .uri("lb://user-service"))

                // 4. INVENTORY SERVICE
                .route("inventory_route", r -> r.path("/api/inventory/**")
                        .filters(f -> f.filter(authFilter.apply(new JwtAuthenticationFilter.Config()))
                                .requestRateLimiter(config -> {
                                    config.setRateLimiter(redisRateLimiter());
                                    config.setKeyResolver(userKeyResolver());
                                }))
                        .uri("lb://inventory-service"))

                // 5. ORDER SERVICE
                .route("order_route", r -> r.path("/api/orders/**")
                        .filters(f -> f.filter(authFilter.apply(new JwtAuthenticationFilter.Config()))
                                .requestRateLimiter(config -> {
                                    config.setRateLimiter(redisRateLimiter());
                                    config.setKeyResolver(userKeyResolver());
                                }))
                        .uri("lb://order-service"))
                .build();
    }

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        // replenishmentRate: 10 запросов в сек, burstCapacity: 20 запросов
        return new RedisRateLimiter(10, 20);
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Пытаемся достать заголовок, который проставил JwtAuthenticationFilter
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            return Mono.just(Objects.requireNonNullElseGet(userId, () -> Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(addr -> addr.getAddress().getHostAddress())
                    .orElse("anonymous")));
            // Если пользователя нет, откатываемся к IP
        };
    }

    @LoadBalanced
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public CommandLineRunner initSwagger(SwaggerUiConfigProperties swaggerUiConfigProperties) {
        return args -> {
            swaggerUiConfigProperties.getUrls().clear(); // очищаем старое
            swaggerUiConfigProperties.getUrls().add(new AbstractSwaggerUiConfigProperties.SwaggerUrl("auth-service", "/api/auth/v3/api-docs", "auth-service"));
            swaggerUiConfigProperties.getUrls().add(new AbstractSwaggerUiConfigProperties.SwaggerUrl("user-service", "/api/users/v3/api-docs", "user-service"));
            swaggerUiConfigProperties.getUrls().add(new AbstractSwaggerUiConfigProperties.SwaggerUrl("inventory-service", "/api/inventory/v3/api-docs", "inventory-service"));
            swaggerUiConfigProperties.getUrls().add(new AbstractSwaggerUiConfigProperties.SwaggerUrl("product-service", "/api/products/v3/api-docs", "product-service"));
            swaggerUiConfigProperties.getUrls().add(new AbstractSwaggerUiConfigProperties.SwaggerUrl("order-service", "/api/orders/v3/api-docs", "order-service"));
        };
    }
}