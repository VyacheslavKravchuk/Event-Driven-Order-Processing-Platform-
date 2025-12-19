package com.inovexx.notification_service.config.send;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.FirebaseMessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    /**
     * Sends a push notification to a specific device token.
     *
     * @param deviceToken The FCM registration token of the recipient device.
     * @param title       The notification title.
     * @param body        The notification body text.
     */
    @Cacheable(value = "userSend", key = "'Push:' + #id")
    public void sendPushNotification(String deviceToken, String title, String body) {
        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Message message = Message.builder()
                .setToken(deviceToken) // Отправка конкретному устройству
                .setNotification(notification)
                // Можно добавить дополнительные данные (payload)
                // .putData("orderId", "12345")
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent push message: {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("Error sending push notification to token {}: {}", deviceToken, e.getMessage());
            // Обработка ошибок отправки
        }
    }
}
