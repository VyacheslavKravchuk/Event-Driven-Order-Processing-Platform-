package com.inovexx.notification_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Класс конфигурации MongoDB.
 * Обычно требуется только для расширенной кастомизации.
 */
@Configuration
// Указывает Spring Boot, где искать интерфейсы репозиториев (например, NotificationRepository)
@EnableMongoRepositories(basePackages = "com.inovexx.notification_service.repository")
public class MongoConfig {

    // Если вы используете Вариант 1 выше (application.properties), этот класс может быть пустым,
    // он просто подтверждает, что репозитории будут найдены.

    /*
     * Пример кастомного бина MongoTemplate, если вам нужно использовать его напрямую
     * вместо Spring Data Repositories.
     *
     * @Autowired
     * private MongoClient mongoClient;
     *
     * @Bean
     * public MongoTemplate mongoTemplate() throws Exception {
     *     return new MongoTemplate(mongoClient, "notification-db");
     * }
     */

    /*
     * Если вам нужно добавить кастомные конвертеры дат/других типов данных,
     * вы можете переопределить метод configureConversionService() здесь.
     */
}

