package com.inovexx.notification_service.service.impl;

import com.inovexx.notification_service.enums.NotificationStatus;
import com.inovexx.notification_service.enums.NotificationType;
import com.inovexx.notification_service.model.Notification;
import com.inovexx.notification_service.repository.NotificationRepository;
import com.inovexx.notification_service.service.EmailService;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final Configuration freemarkerConfig;
    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void sendNotification(String userEmail, String subject, Map<String, Object> model, NotificationType type) {
        // Создаем запись PENDING перед отправкой
        Notification notification = createBaseNotification(userEmail, subject, type, NotificationStatus.PENDING);
        notification = notificationRepository.save(notification);

        try {
            Template template = freemarkerConfig.getTemplate(type.getTemplateName());
            String htmlBody = FreeMarkerTemplateUtils.processTemplateIntoString(template, model);

            notification.setContent(htmlBody);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(userEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(OffsetDateTime.now());
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.ERROR);
            notification.setErrorMessage(e.getMessage());
            log.error("Ошибка отправки для {}: {}", userEmail, e.getMessage());
            throw new RuntimeException("Email delivery failed", e);
        } finally {
            notificationRepository.save(notification);
        }
    }

    /**
     * Реализация Варианта Б: Сохранение уведомления, которое было отсечено Rate Limiter-ом
     */
    @Override
    public void saveSkippedNotification(String email, String subject, NotificationType type, String reason) {
        Notification notification = createBaseNotification(email, subject, type, NotificationStatus.SKIPPED_BY_RATE_LIMIT);
        notification.setErrorMessage(reason); // Записываем причину (например, "Rate limit exceeded in Redis")

        notificationRepository.save(notification);
        log.info("Уведомление для {} сохранено со статусом SKIPPED (Rate Limit)", email);
    }

    /**
     * Вспомогательный метод для создания объекта Notification
     */
    private Notification createBaseNotification(String email, String subject, NotificationType type, NotificationStatus status) {
        Notification notification = new Notification();
        notification.setRecipient(email);
        notification.setSubject(subject);
        notification.setType(type);
        notification.setStatus(status);
        notification.setCreatedAt(OffsetDateTime.now());
        return notification;
    }
}
