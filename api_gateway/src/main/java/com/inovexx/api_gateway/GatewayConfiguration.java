package com.inovexx.api_gateway;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GatewayConfiguration {

    @Bean
    public RouteLocator myRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("user_route_auth", r -> r.path("/users_auth/**")
                        .uri("lb://auth-service"))
                .build();
    }

    @LoadBalanced
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}