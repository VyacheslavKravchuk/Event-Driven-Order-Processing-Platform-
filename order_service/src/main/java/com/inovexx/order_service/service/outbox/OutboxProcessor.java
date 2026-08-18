package com.inovexx.order_service.service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inovexx.order_service.dto.OrderStatusUpdatePayload;
import com.inovexx.order_service.entity.Order;
import com.inovexx.order_service.entity.OrderItem;
import com.inovexx.order_service.enums.EventStatus;
import com.inovexx.order_service.enums.OutboxEventType;
import com.inovexx.order_service.events.OrderNotificationEvent;
import com.inovexx.order_service.events.OutboxEvent;
import com.inovexx.order_service.exception.OrderNotFoundException;
import com.inovexx.order_service.repository.OrderRepository;
import com.inovexx.order_service.repository.OutboxRepository;
import com.inovexx.order_service.service.impl.OrderCancellationOrchestrator;
import com.inovexx.order_service.service.impl.OrderSagaOrchestrator;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxProcessor {

    private static final int KAFKA_SEND_TIMEOUT_SECONDS = 10;
    private static final String ORDER_EVENTS_TOPIC = "order.events";

    private final OutboxRepository outboxRepository;
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final OrderCancellationOrchestrator orderCancellationOrchestrator;
    private final OutboxFailureService outboxFailureService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleEvent(
            Long eventId,
            Counter processedCounter,
            Counter errorCounter,
            Timer processTimer
    ) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            OutboxEvent event = outboxRepository.findByIdForProcessing(eventId)
                    .orElse(null);

            if (event == null) {
                log.debug("Outbox-{}: событие не найдено или уже захвачено другим воркером", eventId);
                return;
            }

            if (event.isProcessed()) {
                log.debug("Outbox-{}: событие уже обработано", eventId);
                return;
            }

            if (event.getOrderId() == null) {
                throw new IllegalStateException("Outbox event does not contain orderId. eventId=" + eventId);
            }

            if (event.getEventType() == null) {
                throw new IllegalStateException("Outbox event type is null. eventId=" + eventId);
            }

            log.info(
                    "Outbox-{}: начало обработки. type={}, retryCount={}",
                    eventId,
                    event.getEventType(),
                    Optional.ofNullable(event.getRetryCount()).orElse(0)
            );

            dispatchEvent(event);

            markSuccess(event);
            processedCounter.increment();

            log.info("Outbox-{}: успешно обработано", eventId);

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Outbox-{}: ошибка обработки: {}", eventId, e.getMessage(), e);
            outboxFailureService.handleFailure(eventId, e);
        } finally {
            sample.stop(processTimer);
        }
    }

    private void dispatchEvent(OutboxEvent event) {
        OutboxEventType type = event.getEventType();

        switch (type) {
            case ORDER_CREATED, START_GRPC_SAGA -> startOrderSaga(event);

            case START_CANCEL_SAGA -> startCancelSaga(event);

            case ORDER_PAID -> publishOrderNotification(event, "PAID");

            case ORDER_CANCELLED -> publishOrderNotification(event, "CANCELLED");

            case ORDER_STATUS_UPDATED -> publishStatusUpdatedNotification(event);

            default -> throw new IllegalArgumentException("Unsupported event type: " + type);
        }
    }

    /**
     * Запуск основной саги обработки заказа.
     * Важно: orchestrator.handleOrderCreated(orderId) должен быть идемпотентным.
     */
    private void startOrderSaga(OutboxEvent event) {
        UUID orderId = event.getOrderId();

        log.info("Outbox-{}: запуск order saga для заказа {}", event.getId(), orderId);

        orderSagaOrchestrator.handleOrderCreated(orderId);

        log.info("Outbox-{}: order saga успешно завершила запуск для заказа {}", event.getId(), orderId);
    }

    /**
     * Запуск компенсационной саги отмены.
     * Здесь нельзя повторно вызывать requestCancellation(), потому что это только
     * постановка статуса CANCELLATION_REQUESTED, а не реальная компенсация.
     */
    private void startCancelSaga(OutboxEvent event) {
        UUID orderId = event.getOrderId();

        log.info("Outbox-{}: запуск cancellation saga для заказа {}", event.getId(), orderId);

        orderCancellationOrchestrator.handleCancellationRequested(orderId);

        log.info("Outbox-{}: cancellation saga успешно завершила запуск для заказа {}", event.getId(), orderId);
    }

    private void publishStatusUpdatedNotification(OutboxEvent event) {
        try {
            OrderStatusUpdatePayload payload = objectMapper.readValue(
                    event.getPayload(),
                    OrderStatusUpdatePayload.class
            );

            if (payload.status() == null || payload.status().isBlank()) {
                throw new IllegalStateException(
                        "ORDER_STATUS_UPDATED payload does not contain status. eventId=" + event.getId()
                );
            }

            publishOrderNotification(event, payload.status().trim().toUpperCase());

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse payload for ORDER_STATUS_UPDATED. eventId=" + event.getId(),
                    e
            );
        }
    }

    /**
     * Публикует notification-событие во внешний Kafka topic.
     * Используется только для интеграционных уведомлений.
     */
    private void publishOrderNotification(OutboxEvent event, String status) {
        Long eventId = event.getId();
        UUID orderId = event.getOrderId();

        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));

            String customerEmail = normalize(order.getCustomerEmail());
            if (customerEmail == null) {
                throw new IllegalStateException(
                        "Customer email is missing for orderId=" + orderId
                );
            }

            List<OrderNotificationEvent.OrderItemRecord> itemRecords =
                    order.getOrderItems() == null
                            ? List.of()
                            : order.getOrderItems().stream()
                            .map(this::toItemRecord)
                            .toList();

            OrderNotificationEvent payload = new OrderNotificationEvent(
                    orderId,
                    order.getUserId(),
                    customerEmail,
                    status,
                    order.getTotalAmount(),
                    itemRecords
            );

            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    ORDER_EVENTS_TOPIC,
                    orderId.toString(),
                    payload
            );

            addHeaders(record, eventId, event.getEventType(), orderId);

            kafkaTemplate.send(record).get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info(
                    "Outbox-{}: Kafka publish successful. orderId={}, eventType={}, status={}",
                    eventId,
                    orderId,
                    event.getEventType(),
                    status
            );

        } catch (Exception e) {
            throw new RuntimeException(
                    "Kafka publish failed for outbox eventId=" + eventId + ", orderId=" + orderId,
                    e
            );
        }
    }

    private OrderNotificationEvent.OrderItemRecord toItemRecord(OrderItem item) {
        return new OrderNotificationEvent.OrderItemRecord(
                String.valueOf(item.getProductId()),
                item.getQuantity(),
                item.getPrice()
        );
    }

    private void markSuccess(OutboxEvent event) {
        event.setProcessed(true);
        event.setStatus(EventStatus.SUCCESS);
        event.setProcessedAt(OffsetDateTime.now());
        event.setLastErrorMessage(null);
        outboxRepository.save(event);
    }

    private void addHeaders(
            ProducerRecord<String, Object> record,
            Long eventId,
            OutboxEventType type,
            UUID orderId
    ) {
        record.headers().add(new RecordHeader(
                "eventId",
                String.valueOf(eventId).getBytes(StandardCharsets.UTF_8)
        ));
        record.headers().add(new RecordHeader(
                "eventType",
                type.name().getBytes(StandardCharsets.UTF_8)
        ));
        record.headers().add(new RecordHeader(
                "orderId",
                orderId.toString().getBytes(StandardCharsets.UTF_8)
        ));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
