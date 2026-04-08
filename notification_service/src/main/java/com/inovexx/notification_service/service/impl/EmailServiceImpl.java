package com.inovexx.notification_service.service.impl;

import com.inovexx.notification_service.enums.NotificationStatus;
import com.inovexx.notification_service.enums.NotificationType;
import com.inovexx.notification_service.model.Notification;
import com.inovexx.notification_service.repository.NotificationRepository;
import com.inovexx.notification_service.service.EmailService;
import freemarker.template.Configuration;
import freemarker.template.Template;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final Configuration freemarkerConfig;
    private final NotificationRepository notificationRepository;
    private final ProxyManager<String> buckets; // Наш Redis Manager для лимитов

    @Override
    public boolean sendNotification(String userEmail, String subject, Map<String, Object> model, NotificationType type) {

        // 1. Проверяем лимит (например, 5 писем в 10 минут для этого email)
        Bucket bucket = buckets.builder().build(userEmail, this::getBucketConfiguration);

        if (!bucket.tryConsume(1)) {
            // ЛИМИТ ПРЕВЫШЕН: Сохраняем в БД как пропущенное
            saveSkippedNotification(userEmail, subject, type, "Rate limit exceeded in Redis");
            log.warn("Rate limit достигнут для {}. Отправка [{}] отменена.", userEmail, type);
            return false;
        }

        // 2. Лимит ОК: Создаем запись PENDING
        Notification notification = createBaseNotification(userEmail, subject, type, NotificationStatus.PENDING);
        notification = notificationRepository.save(notification);

        try {
            // 3. Генерация контента
            Template template = freemarkerConfig.getTemplate(type.getTemplateName());
            String htmlBody = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);
            notification.setContent(htmlBody);

            // 4. Настройка и отправка письма
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(userEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            // ЯВНО указываем отправителя, чтобы MailHog не ругался на "Invalid sender"
            helper.setFrom("no-reply@inovexx.com");

            mailSender.send(message);

            // 5. Успех
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(OffsetDateTime.now());
            log.info("Email [{}] успешно отправлен на {}", type, userEmail);

        } catch (Exception e) {
            // 6. Ошибка
            notification.setStatus(NotificationStatus.ERROR);
            notification.setErrorMessage(e.getMessage());
            log.error("Ошибка при отправке письма на {}: {}", userEmail, e.getMessage());

            // Пробрасываем исключение, чтобы Kafka мог сделать Retry (если вызвано из Consumer)
            throw new RuntimeException("Email delivery failed", e);
        } finally {
            notificationRepository.save(notification);
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void saveSkippedNotification(String email, String subject, NotificationType type, String reason) {
        Notification notification = createBaseNotification(email, subject, type, NotificationStatus.SKIPPED_BY_RATE_LIMIT);
        notification.setErrorMessage(reason);
        notificationRepository.save(notification);
    }

    private Notification createBaseNotification(String email, String subject, NotificationType type, NotificationStatus status) {
        Notification notification = new Notification();
        notification.setRecipient(email);
        notification.setSubject(subject);
        notification.setType(type);
        notification.setStatus(status);
        notification.setCreatedAt(OffsetDateTime.now());
        return notification;
    }

    private BucketConfiguration getBucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(10))))
                .build();
    }
}

