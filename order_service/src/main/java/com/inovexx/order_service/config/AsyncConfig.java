package com.inovexx.order_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 1. Имя потоков (то, что мы обсуждали ранее)
        executor.setThreadNamePrefix("OrderSaga-");

        // 2. Основное количество потоков, которые всегда активны
        executor.setCorePoolSize(10);

        // 3. Максимальное количество потоков при высокой нагрузке
        executor.setMaxPoolSize(50);

        // 4. Очередь для задач, если все потоки заняты
        executor.setQueueCapacity(100);

        // 5. Как вести себя, если очередь полна и потоков больше нет?
        // CallerRunsPolicy заставит поток, создавший заказ, сам выполнить сагу
        // (это замедлит создание, но спасет от потери данных)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }
}