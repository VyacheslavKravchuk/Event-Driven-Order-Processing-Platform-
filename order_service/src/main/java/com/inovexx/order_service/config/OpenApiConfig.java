package com.inovexx.order_service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info().title("API Service").version("v1"))
                // 1. Указываем сервер Gateway
                .servers(List.of(
                        new Server().url("http://localhost:8088").description("Gateway Server")
                ))
                // 2. Добавляем компоненты безопасности (описываем, ЧТО такое JWT)
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // 3. Добавляем глобальное требование безопасности (применяем замок ко всем методам)
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName));
    }
}