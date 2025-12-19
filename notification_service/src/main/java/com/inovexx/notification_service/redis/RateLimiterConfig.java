package com.inovexx.notification_service.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RateLimiterConfig {

    @Bean
    public RedisTemplate<String, Long> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Long> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer()); // Ключи - строки (например, ID пользователя)
        template.setValueSerializer(new org.springframework.data
                .redis.serializer
                .GenericToStringSerializer<>(Long.class)); // Значения - long (кол-во запросов)
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new org.springframework.data
                .redis.serializer.GenericToStringSerializer<>(Long.class));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RateLimiterService rateLimiterService(RedisTemplate<String,
            Long> redisTemplate) {
        return new RateLimiterService(redisTemplate);
    }

}
