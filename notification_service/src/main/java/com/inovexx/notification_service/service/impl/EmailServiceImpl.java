package com.inovexx.notification_service.service.impl;

import com.inovexx.notification_service.service.EmailService;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.util.Map;
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final Configuration freemarkerConfig;

    public EmailServiceImpl(JavaMailSender mailSender, Configuration freemarkerConfig) {
        this.mailSender = mailSender;
        this.freemarkerConfig = freemarkerConfig;
    }

    @Async
    @Override
    public void sendNotification(String userEmail, String subject, Map<String, Object> model, String templateName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            // Загружаем шаблон динамически по имени
            Template template = freemarkerConfig.getTemplate(templateName);

            // Генерируем HTML-контент
            String htmlBody = FreeMarkerTemplateUtils
                    .processTemplateIntoString(template, model);

            helper.setTo(userEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.setFrom("no-reply@inovexx.com");

            mailSender.send(message);
            log.info("Email '{}' успешно отправлен на {}", subject, userEmail);

        } catch (Exception e) {
            log.error("Ошибка при отправке письма [{}]: {}", templateName, e.getMessage());
        }
    }

}
