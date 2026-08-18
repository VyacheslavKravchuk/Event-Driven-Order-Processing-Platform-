package com.inovexx.notification_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

/**
 * Класс конфигурации для включения асинхронного выполнения методов.
 */
@Configuration
@EnableAsync // Активирует поддержку обработки асинхронных методов Spring
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Определяет кастомный Executor (пул потоков) для выполнения асинхронных задач.
     * Это лучшая практика, чем использование дефолтного SimpleAsyncTaskExecutor.
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Устанавливаем базовое количество потоков, которые всегда активны
        executor.setCorePoolSize(5);
        // Максимальное количество потоков, которое может создать пул
        executor.setMaxPoolSize(20);
        // Размер очереди для задач, ожидающих выполнения
        executor.setQueueCapacity(500);
        // Имя для потоков, полезно для отладки
        executor.setThreadNamePrefix("NotificationAsync-");
        executor.initialize();
        return executor;
    }
}
