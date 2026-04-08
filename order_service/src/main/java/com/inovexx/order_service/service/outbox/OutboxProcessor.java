package com.inovexx.order_service.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.order_service.dto.OrderStatusUpdatePayload;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.enums.EventStatus;
import com.inovexx.order_service.events.OrderNotificationEvent;
import com.inovexx.order_service.events.OutboxEvent;
import com.inovexx.order_service.exception.OrderNotFoundException;
import com.inovexx.order_service.repository.OrderRepository;
import com.inovexx.order_service.repository.OutboxRepository;
import com.inovexx.order_service.service.OrderService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxProcessor {

    private static final int KAFKA_SEND_TIMEOUT_SECONDS = 10;

    private final OutboxRepository outboxRepository;
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderService orderService;
    private final OutboxFailureService outboxFailureService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleEvent(Long eventId,
                                   Counter processedCounter,
                                   Counter errorCounter,
                                   Timer processTimer) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            OutboxEvent event = outboxRepository.findByIdForProcessing(eventId)
                    .orElse(null);

            if (event == null) {
                log.debug("Outbox-{}: событие не найдено или недоступно для обработки", eventId);
                return;
            }

            if (Boolean.TRUE.equals(event.getProcessed())) {
                log.debug("Outbox-{}: событие уже обработано, пропускаем", eventId);
                return;
            }

            Long orderId = event.getOrderId();
            if (orderId == null) {
                throw new IllegalStateException("Outbox event does not contain orderId. eventId=" + eventId);
            }

            log.info(
                    "Outbox-{}: начинаем обработку. eventType={}, retryCount={}",
                    eventId,
                    event.getEventType(),
                    Optional.ofNullable(event.getRetryCount()).orElse(0)
            );

            dispatchEvent(event);

            event.setProcessed(true);
            event.setStatus(EventStatus.SUCCESS);
            event.setProcessedAt(OffsetDateTime.now());
            event.setLastErrorMessage(null);

            outboxRepository.save(event);

            processedCounter.increment();

            log.info("Outbox-{}: событие успешно обработано", eventId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Outbox-{}: ошибка обработки: {}", eventId, e.getMessage(), e);
            outboxFailureService.handleFailure(eventId, e);
        } finally {
            sample.stop(processTimer);
        }
    }

//    private void dispatchEvent(OutboxEvent event) {
//        String eventType = event.getEventType();
//
//        switch (eventType) {
//            case "START_GRPC_SAGA" -> startGrpcSaga(event);
//            case "ORDER_CREATED" -> publishOrderEvent(event, "order.events", "NEW");
//            case "ORDER_PAID" -> publishOrderEvent(event, "order.events", "PAID");
//            case "ORDER_STATUS_UPDATED" -> publishOrderEvent(event, "order.events", "SHIPPED");
//            default -> throw new IllegalArgumentException(
//                    "Unsupported outbox event type: " + eventType + ", eventId=" + event.getId()
//            );
//        }
//    }

    private void dispatchEvent(OutboxEvent event) {
        String eventType = event.getEventType();
        switch (eventType) {
            case "START_GRPC_SAGA" -> startGrpcSaga(event);
            case "ORDER_CREATED" -> publishOrderEvent(event, "order.events", "NEW");
            case "ORDER_PAID" -> publishOrderEvent(event, "order.events", "PAID");
            case "ORDER_STATUS_UPDATED" -> handleStatusUpdate(event); // Вызываем отдельный метод
            case "START_CANCEL_SAGA" -> startCancelSaga(event);
            default -> throw new IllegalArgumentException("Unsupported event type: " + eventType);
        }
    }

    private void startCancelSaga(OutboxEvent event) {
        log.info("Инициализация Saga отмены для заказа: {}", event.getOrderId());

        // Здесь должна быть логика вызова Saga-оркестратора
        // или прямого вызова компенсационных методов (Refund/Release)
        // Пример:
        orderService.processCancelSaga(event.getOrderId(), " Причина ");
    }

    private void handleStatusUpdate(OutboxEvent event) {
        // В прошлом шаге мы сохранили статус в payload как JSON.
        // Нам нужно достать его, чтобы отправить в Kafka правильный статус.
        try {
            // Десериализуем payload из OutboxEvent обратно, чтобы узнать статус
            OrderStatusUpdatePayload payload = objectMapper.readValue(
                    event.getPayload(),
                    OrderStatusUpdatePayload.class
            );

            // Отправляем в Kafka реальный статус (например, "SHIPPED")
            publishOrderEvent(event, "order.events", payload.status());
        } catch (Exception e) {
            log.error("Ошибка при разборе payload для обновления статуса", e);
            // Как фолбек можно отправить "UPDATED", но лучше упасть и разобраться
            publishOrderEvent(event, "order.events", "UPDATED");
        }
    }

    private void startGrpcSaga(OutboxEvent event) {
        Long orderId = event.getOrderId();

        // ВАЖНО:
        // processOrderSaga(orderId) должен быть идемпотентным,
        // потому что outbox по природе допускает повторные попытки.
        orderService.processOrderSaga(orderId);

        log.info(
                "Outbox-{}: gRPC saga успешно запущена для заказа {}",
                event.getId(),
                orderId
        );
    }

    private void publishOrderEvent(OutboxEvent event, String topic, String status) {
        Long eventId = event.getId();
        Long orderId = event.getOrderId();

        try {
            // 1. Получаем актуальное состояние заказа из БД
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));

            // 2. Валидация критических данных (email) перед отправкой
            String customerEmail = order.getCustomerEmail();
            if (customerEmail == null || customerEmail.isBlank()) {
                log.error("Outbox-{}: В заказе #{} отсутствует email. Отправка события отменена.", eventId, orderId);
                throw new IllegalStateException("Критическая ошибка: в заказе #" + orderId + " отсутствует email");
            }

            // 3. Формируем контракт события для Kafka
            List<OrderNotificationEvent.OrderItemRecord> itemRecords = order.getOrderItems().stream()
                    .map(item -> new OrderNotificationEvent.OrderItemRecord(
                            String.valueOf(item.getProductId()),
                            item.getQuantity(),
                            item.getPrice()
                    ))
                    .toList();

            OrderNotificationEvent payload = new OrderNotificationEvent(
                    orderId,
                    order.getUserId(),
                    customerEmail.trim(), // Гарантируем отсутствие лишних пробелов
                    status,
                    order.getTotalAmount(),
                    itemRecords
            );

            // 4. Отправка объекта напрямую в KafkaTemplate.
            // ВАЖНО: Мы НЕ вызываем objectMapper.writeValueAsString().
            // JsonSerializer в Spring Kafka сделает это автоматически один раз.
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    topic,
                    String.valueOf(orderId), // Key (String)
                    payload                  // Value (Object -> станет JSON)
            );

            // Добавляем технические заголовки
            addHeaders(record, eventId, event.getEventType(), orderId);

            // 5. Синхронное ожидание (с логированием результата)
            log.debug("Outbox-{}: Попытка отправки события для заказа #{} в топик {}", eventId, orderId, topic);

            kafkaTemplate.send(record)
                    .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("Outbox-{}: Событие {} успешно отправлено в Kafka (заказ #{}, статус {})",
                    eventId, event.getEventType(), orderId, status);

        } catch (Exception e) {
            log.error("Outbox-{}: Ошибка при публикации события заказа #{} в Kafka: {}",
                    eventId, orderId, e.getMessage());
            // Пробрасываем исключение, чтобы транзакция OutboxProcessor откатилась и событие осталось для Retry
            throw new RuntimeException("Kafka publish failed for event " + eventId, e);
        }
    }

    private void addHeaders(ProducerRecord<String, Object> record, Long eventId, String type, Long orderId) {
        record.headers().add(new RecordHeader("eventId", String.valueOf(eventId).getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("eventType", type.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("orderId", String.valueOf(orderId).getBytes(StandardCharsets.UTF_8)));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
