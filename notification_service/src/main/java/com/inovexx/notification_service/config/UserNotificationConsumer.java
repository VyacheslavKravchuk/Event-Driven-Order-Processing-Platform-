package com.inovexx.notification_service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.notification_service.dto.events.OrderNotificationEvent;
import com.inovexx.notification_service.dto.events.RegisteredNotificationEvent;
import com.inovexx.notification_service.enums.NotificationType;
import com.inovexx.notification_service.service.EmailService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.mail.MailException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserNotificationConsumer {

    private static final String RATE_LIMIT_BUCKET_PREFIX = "rate-limit:email:";

    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final ProxyManager<String> buckets;

    @Value("${app.notification.rate-limit.capacity:5}")
    private long rateLimitCapacity;

    @Value("${app.notification.rate-limit.duration-minutes:10}")
    private int rateLimitDuration;

    @Value("${app.notification.support-email:support@inovexx.com}")
    private String supportEmail;

    @RetryableTopic(
            attempts = "${app.notification.retry.attempts:3}",
            backoff = @Backoff(delayExpression = "${app.notification.retry.delay-ms:300000}"),
            include = {MailException.class}
    )
    @KafkaListener(
            topics = "${app.notification.kafka.topics.user-created:user.created}",
            groupId = "${app.notification.kafka.group-id:notification-group}"
    )
    public void consumeUserRegistrationEvent(ConsumerRecord<String, String> record) {
        logIncomingRecord("РЕГИСТРАЦИЯ_ПОЛЬЗОВАТЕЛЯ", record);
        try {
            RegisteredNotificationEvent event = parseRegistrationEvent(record.value());
            String email = requireEmail(event.email(), "событие регистрации");

            processEvent(
                    email,
                    "Регистрация прошла успешно!",
                    event,
                    NotificationType.REGISTRATION
            );

            log.info(
                    "Уведомление о регистрации успешно обработано. email={}, topic={}, partition={}, offset={}",
                    maskEmail(email), record.topic(), record.partition(), record.offset()
            );
        } catch (NonRetryableNotificationException e) {
            log.warn(
                    "Событие регистрации пропущено без повторной попытки. причина={}, topic={}, partition={}, offset={}",
                    e.getMessage(), record.topic(), record.partition(), record.offset()
            );
        }
    }

    @RetryableTopic(
            attempts = "${app.notification.retry.attempts:3}",
            backoff = @Backoff(delayExpression = "${app.notification.retry.delay-ms:300000}"),
            include = {MailException.class}
    )
    @KafkaListener(
            topics = "${app.notification.kafka.topics.order-events:order.events}",
            groupId = "${app.notification.kafka.group-id:notification-group}"
    )
    public void consumeOrderEvent(ConsumerRecord<String, String> record) {
        logIncomingRecord("СОБЫТИЕ_ЗАКАЗА", record);
        try {
            OrderNotificationEvent event = parseOrderEvent(record.value());
            validateOrderEvent(event);

            String normalizedStatus = normalizeStatus(event.status());

            if ("IN_PROGRESS".equals(normalizedStatus)) {
                log.debug(
                        "Событие заказа пропущено согласно бизнес-правилу. orderId={}, status={}, topic={}, partition={}, offset={}",
                        event.orderId(), normalizedStatus, record.topic(), record.partition(), record.offset()
                );
                return;
            }

            NotificationType type = resolveType(normalizedStatus);
            String subject = getOrderSubject(normalizedStatus, event.orderId());

            Optional<String> emailOpt = resolveOrderEmail(event);
            if (emailOpt.isEmpty()) {
                log.warn(
                        "Уведомление по заказу пропущено: отсутствует email клиента. orderId={}, status={}, topic={}, partition={}, offset={}",
                        event.orderId(), normalizedStatus, record.topic(), record.partition(), record.offset()
                );
                return;
            }

            processEvent(emailOpt.get(), subject, event, type);

            log.info(
                    "Уведомление по заказу успешно обработано. orderId={}, status={}, email={}, topic={}, partition={}, offset={}",
                    event.orderId(), normalizedStatus, maskEmail(emailOpt.get()),
                    record.topic(), record.partition(), record.offset()
            );
        } catch (NonRetryableNotificationException e) {
            log.warn(
                    "Событие заказа пропущено без повторной попытки. причина={}, topic={}, partition={}, offset={}",
                    e.getMessage(), record.topic(), record.partition(), record.offset()
            );
        }
    }

    private RegisteredNotificationEvent parseRegistrationEvent(String rawMessage) {
        String message = requirePayload(rawMessage);
        try {
            return objectMapper.readValue(message, RegisteredNotificationEvent.class);
        } catch (JsonProcessingException e) {
            throw new NonRetryableNotificationException("Некорректный JSON события регистрации", e);
        }
    }

    private OrderNotificationEvent parseOrderEvent(String rawMessage) {
        String message = unwrapPossiblyDoubleSerializedJson(requirePayload(rawMessage));
        try {
            return objectMapper.readValue(message, OrderNotificationEvent.class);
        } catch (JsonProcessingException e) {
            throw new NonRetryableNotificationException("Некорректный JSON события заказа", e);
        }
    }

    private void validateOrderEvent(OrderNotificationEvent event) {
        if (event == null) {
            throw new NonRetryableNotificationException("Событие заказа пустое после десериализации");
        }
        if (isBlank(event.orderId())) {
            throw new NonRetryableNotificationException("В событии заказа отсутствует orderId");
        }
        if (isBlank(event.status())) {
            throw new NonRetryableNotificationException("В событии заказа отсутствует status");
        }
    }

    private Optional<String> resolveOrderEmail(OrderNotificationEvent event) {
        if (isBlank(event.customerEmail())) {
            return Optional.empty();
        }
        return Optional.of(event.customerEmail().trim());
    }

    private void processEvent(String email, String subject, Object eventDto, NotificationType type) {
        String normalizedEmail = requireEmail(email, "событие уведомления");

        Bucket bucket = buildBucket(normalizedEmail);
        if (!bucket.tryConsume(1)) {
            log.warn("Превышен лимит запросов (Rate limit). email={}, type={}, subject={}",
                    maskEmail(normalizedEmail), type, subject);
            emailService.saveSkippedNotification(
                    normalizedEmail,
                    subject,
                    type,
                    "Превышен лимит запросов в Redis"
            );
            return;
        }

        Map<String, Object> model = buildTemplateModel(eventDto);
        try {
            emailService.sendNotification(normalizedEmail, subject, model, type);
            log.info("Уведомление отправлено. type={}, email={}", type, maskEmail(normalizedEmail));
        } catch (MailException e) {
            log.warn("Временный сбой почтового провайдера. type={}, email={}, причина={}",
                    type, maskEmail(normalizedEmail), e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new NonRetryableNotificationException(
                    "Ошибка отрисовки или отправки уведомления (без повтора)",
                    e
            );
        }
    }

    private Map<String, Object> buildTemplateModel(Object eventDto) {
        try {
            Map<String, Object> model = objectMapper.convertValue(eventDto, new TypeReference<>() {});
            model.put("supportEmail", supportEmail);
            model.put("year", LocalDate.now().getYear());
            return model;
        } catch (IllegalArgumentException e) {
            throw new NonRetryableNotificationException("Не удалось преобразовать DTO события в модель шаблона", e);
        }
    }

    private Bucket buildBucket(String email) {
        String bucketKey = RATE_LIMIT_BUCKET_PREFIX + email.toLowerCase(Locale.ROOT);
        return buckets.builder().build(bucketKey, this::getBucketConfiguration);
    }

    private BucketConfiguration getBucketConfiguration() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(rateLimitCapacity)
                .refillIntervally(rateLimitCapacity, Duration.ofMinutes(rateLimitDuration))
                .build();
        return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
    }

    private NotificationType resolveType(String status) {
        return switch (status) {
            case "NEW", "CREATED" -> NotificationType.ORDER_NEW;
            case "RESERVED"       -> NotificationType.ORDER_RESERVED;
            case "PAID"           -> NotificationType.ORDER_PAID;
            case "SHIPPED"        -> NotificationType.ORDER_SHIPPED;
            case "COMPLETED"      -> NotificationType.ORDER_COMPLETED;
            case "CANCELLED"      -> NotificationType.ORDER_CANCELLED;
            default -> throw new NonRetryableNotificationException("Неподдерживаемый статус заказа: " + status);
        };
    }

    private String getOrderSubject(String status, String orderId) {
        return switch (status) {
            case "NEW", "CREATED" -> "Ваш заказ #" + orderId + " принят";
            case "RESERVED"       -> "Товары забронированы #" + orderId;
            case "PAID"           -> "Оплата получена #" + orderId;
            case "SHIPPED"        -> "Заказ #" + orderId + " в пути";
            case "COMPLETED"      -> "Заказ #" + orderId + " доставлен";
            case "CANCELLED"      -> "Заказ #" + orderId + " отменен";
            default               -> "Обновление по заказу #" + orderId;
        };
    }

    private String unwrapPossiblyDoubleSerializedJson(String message) {
        String trimmed = message.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            try {
                log.debug("Обнаружен дважды сериализованный JSON, распаковка полезной нагрузки");
                return objectMapper.readValue(trimmed, String.class);
            } catch (JsonProcessingException e) {
                throw new NonRetryableNotificationException("Не удалось распаковать дважды сериализованный JSON", e);
            }
        }
        return trimmed;
    }

    private void logIncomingRecord(String eventType, ConsumerRecord<String, String> record) {
        log.info(
                "Получено сообщение из Kafka. eventType={}, topic={}, partition={}, offset={}, key={}, payloadSize={}",
                eventType,
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                record.value() == null ? 0 : record.value().length()
        );
    }

    private String requirePayload(String payload) {
        if (isBlank(payload)) {
            throw new NonRetryableNotificationException("Полезная нагрузка сообщения Kafka пуста");
        }
        return payload.trim();
    }

    private String requireEmail(String email, String context) {
        if (isBlank(email)) {
            throw new NonRetryableNotificationException("Email отсутствует в " + context);
        }
        return email.trim();
    }

    private String normalizeStatus(String status) {
        if (isBlank(status)) {
            throw new NonRetryableNotificationException("Статус заказа пуст");
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String maskEmail(String email) {
        if (isBlank(email) || !email.contains("@")) {
            return "***";
        }
        String trimmed = email.trim();
        int atIndex = trimmed.indexOf('@');
        if (atIndex <= 1) {
            return "***" + trimmed.substring(atIndex);
        }
        return trimmed.charAt(0) + "***" + trimmed.substring(atIndex);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @DltHandler
    public void handleDlt(
            String payload,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String dltTopic,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer originalPartition,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String exceptionClass,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage
    ) {
        log.error(
                "Сообщение перемещено в DLT. dltTopic={}, originalTopic={}, originalPartition={}, originalOffset={}, exceptionClass={}, exceptionMessage={}, payloadSize={}",
                dltTopic,
                originalTopic,
                originalPartition,
                originalOffset,
                exceptionClass,
                exceptionMessage,
                payload == null ? 0 : payload.length()
        );
    }

    private static final class NonRetryableNotificationException extends RuntimeException {
        private NonRetryableNotificationException(String message) {
            super(message);
        }

        private NonRetryableNotificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}