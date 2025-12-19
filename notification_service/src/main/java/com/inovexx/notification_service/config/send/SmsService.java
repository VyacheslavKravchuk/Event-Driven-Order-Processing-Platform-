package com.inovexx.notification_service.config.send;

import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    @Value("${twilio.phone.number}")
    private String fromPhoneNumber;

    /**
     * Sends an SMS message using the Twilio API.
     *
     * @param toPhoneNumber The recipient's phone number (e.g., "+79XXXXXXXXX").
     * @param body          The text content of the SMS.
     */
    @Cacheable(value = "userSend", key = "'Sms:' + #id")
    public void sendSms(String toPhoneNumber, String body) {
        try {
            Message message = Message.creator(
                            new PhoneNumber(toPhoneNumber), // Номер получателя
                            new PhoneNumber(fromPhoneNumber), // Ваш номер Twilio
                            body)
                    .create();

            log.info("SMS sent successfully with SID: {}", message.getSid());
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", toPhoneNumber, e.getMessage());
            // Обработка ошибок, например, неверный номер или проблемы с балансом
            throw new RuntimeException("Failed to send SMS", e);
        }
    }
}
