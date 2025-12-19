package com.inovexx.notification_service.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;


import java.time.Instant;
import java.util.Collections;

@Service
public class RateLimiterService {

    private final RedisTemplate<String, Long> redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    // Параметры ведра:
    private final long BUCKET_CAPACITY = 10;
    private final long REFILL_RATE = 1; // 1 токен в секунду

    public RateLimiterService(RedisTemplate<String, Long> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setResultType(Long.class);
        // Загрузите скрипт из файла, или вставьте его как строку здесь
        // В реальном проекте лучше хранить скрипт в файле /resources/rate_limit.lua
        this.rateLimitScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("rate_limit.lua")));
    }

    public boolean allowRequest(String userId) {
        String key = "rate_limit:" + userId;

        // Время в секундах
        long currentTimeSeconds = Instant.now().getEpochSecond();

        // Выполняем LUA-скрипт атомарно на сервере Redis
        Long result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key), // KEYS[1]
                String.valueOf(BUCKET_CAPACITY), // ARGV[1]
                String.valueOf(REFILL_RATE),     // ARGV[2]
                String.valueOf(currentTimeSeconds) // ARGV[3]
        );

        // Скрипт возвращает 1 (разрешено) или 0 (отклонено)
        return result == 1L;
    }
}

