package com.inovexx.order_service.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary // Делаем этот бин основным для приложения
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 1. Поддержка Java 8 Date/Time (LocalDate, OffsetDateTime и т.д.)
        // Без этого вы получите ошибку при попытке сериализовать даты
        mapper.registerModule(new JavaTimeModule());

        // 2. Отключаем запись дат в виде таймстампов (чисел), пишем в ISO-8601 (строки)
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 3. Чтобы приложение не падало, если в JSON придут поля, которых нет в DTO
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 4. Включаем красивый вывод (опционально, удобно для отладки в логах)
        // mapper.enable(SerializationFeature.INDENT_OUTPUT);

        return mapper;
    }
}
