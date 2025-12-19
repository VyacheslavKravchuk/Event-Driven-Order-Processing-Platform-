package com.inovexx.notification_service.redis;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class NotificationLockService {

    @Autowired
    private RedissonClient redissonClient;

    public boolean trySendNotification(String notificationId, Runnable task) {
        // Создание ключа блокировки, уникального для конкретного уведомления
        String lockKey = "notification:lock:" + notificationId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Попытка захватить блокировку с таймаутом ожидания (10с) и TTL (60с)
            boolean isLocked = lock.tryLock(10, 60, TimeUnit.SECONDS);

            if (isLocked) {
                try {
                    // Критическая секция: выполнение задачи по отправке уведомления
                    task.run();
                    return true;
                } finally {
                    lock.unlock(); // Всегда снимаем блокировку после выполнения
                }
            } else {
                // Блокировка не получена (другой сервис уже обрабатывает это уведомление)
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
