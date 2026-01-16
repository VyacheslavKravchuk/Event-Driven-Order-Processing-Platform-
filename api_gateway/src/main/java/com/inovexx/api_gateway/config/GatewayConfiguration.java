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

import java.util.Optional;

@Configuration
public class GatewayConfiguration {
    @Bean
    public RouteLocator myRoutes(RouteLocatorBuilder builder, JwtAuthenticationFilter authFilter) {
        return builder.routes()
                // 1. Auth Service (Rate Limiting по IP)
                .route("auth_route", r -> r.path("/api/auth/**")
                        .filters(f -> f.requestRateLimiter(config -> {
                            config.setRateLimiter(redisRateLimiter());
                            config.setKeyResolver(userKeyResolver());
                        }))
                        .uri("lb://auth-service"))
                // 2. User Service (С проверкой JWT)
                // Route for User (CRUD users, wallets, transactions)
                .route("user_route", r -> r.path(
                                "/api/users/**",
                                "/api/v1/wallets/**",
                                "/api/v1/transactions/**"
                        )
                        .filters(f -> f.filter(authFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("lb://user-service"))
                // 3. Product Service (Каталог товаров)
                .route("product_route", r -> r.path("/api/products/**", "/catalog/**")
                        .filters(f -> f.filter(authFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("lb://product-service"))
                //Inventory Service
                .route("inventory_route", r -> r.path("/api/inventory/**")

                        .filters(f -> f.filter(authFilter.apply(new JwtAuthenticationFilter.Config())))

                        .uri("lb://inventory-service"))
                // 5. Order Service (Заказы)
                .route("order_route", r -> r.path("/api/orders/**")
                        .filters(f -> f.filter(authFilter.apply(new JwtAuthenticationFilter.Config())))
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
            String remoteAddress = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(address -> address.getAddress().getHostAddress())
                    .orElse("127.0.0.1"); // Запасной вариант, если адрес не определен

            return Mono.just(remoteAddress);
        };
    }
    @LoadBalanced
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}