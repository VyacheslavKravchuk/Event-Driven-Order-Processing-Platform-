package com.inovexx.order_service.client.grpc_client;

import com.inovexx.order_service.entity.OrderItem;
import com.inovexx.order_service.mapper.OrderMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import java.util.List;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.inovexx.inventory.grpc.CancelReservationRequest;
import com.inovexx.inventory.grpc.CancelReservationResponse;
import com.inovexx.inventory.grpc.CommitReductionRequest;
import com.inovexx.inventory.grpc.CommitReductionResponse;
import com.inovexx.inventory.grpc.InventoryServiceGrpc;
import com.inovexx.inventory.grpc.OrderItemRequest;
import com.inovexx.inventory.grpc.ReservationError;
import com.inovexx.inventory.grpc.ReserveBatchRequest;
import com.inovexx.inventory.grpc.ReserveBatchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;


/**
 * Клиент для взаимодействия с сервисом Inventory через gRPC.
 * <p>
 * Поддерживает:
 * <ul>
 *   <li>Резервирование партий товаров ({@link #reserveBatchStock}).</li>
 *   <li>Отмену резервации ({@link #cancelBatchReservation}).</li>
 *   <li>Фиксацию уменьшения запасов ({@link #commitBatchReduction}).</li>
 * </ul>
 * Использует Circuit Breaker для защиты от сбоев и метрики для мониторинга.
 */

/**
 * Клиент для работы с inventory gRPC-сервисом.
 *
 * Поддерживает:
 * - идемпотентность
 * - таймауты gRPC-вызовов
 * - метрики Micrometer
 * - MDC-трассировку
 * - единообразную обработку ошибок
 *
 * Важно:
 * если нужна настоящая идемпотентность между ретраями,
 * вызывающая сторона должна передавать стабильный idempotencyKey.
 */

@Slf4j
@RequiredArgsConstructor
@Service
public class InventoryClient {
    // Метрики для мониторинга
    private static final String METRIC_REQUESTS = "inventory.client.requests";
    private static final String METRIC_DURATION = "inventory.client.duration";
    private static final String METRIC_GRPC_ERRORS = "inventory.client.grpc.errors";
    private static final String METRIC_INTERNAL_ERRORS = "inventory.client.internal.errors";

    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryServiceStub;

    private final MeterRegistry meterRegistry;
    private final OrderMapper orderMapper;

    @Value("${inventory.client.deadline-ms:3000}")
    private long deadlineMs;

    /**
     * Резервирует товары под заказ.
     *
     * @param orderId           идентификатор заказа
     * @param items             список товаров для резервации
     * @param idempotencyKey    идемпотентный ключ для повторных запросов
     * @return true, если резервация успешна, иначе false
     */
    @CircuitBreaker(name = "inventoryClient", fallbackMethod = "fallbackReserve")
    @Retry(name = "inventoryClient", fallbackMethod = "fallbackReserve")
    public boolean reserveBatchStock(String orderId, List<OrderItem> items, String idempotencyKey) {
        log.debug("Начало резервации товаров для заказа: orderId={}", orderId);

        validateOrderId(orderId);
        validateDomainItems(items);

        String effectiveIdempotencyKey = resolveIdempotencyKey(idempotencyKey);
        List<OrderItemRequest> grpcItems = orderMapper.toGrpcItemRequestList(items);

        ReserveBatchRequest request = ReserveBatchRequest.newBuilder()
                .setOrderId(orderId)
                .addAllItems(grpcItems)
                .setIdempotencyKey(effectiveIdempotencyKey)
                .build();

        return execute("reserve", orderId, () -> {
            ReserveBatchResponse response = stubWithDeadline().reserveBatchStock(request);
            return handleBusinessResponse(
                    "reserve",
                    orderId,
                    response.getSuccess(),
                    response.getError(),
                    response.getMessage()
            );
        });
    }

    /**
     * Отменяет резервацию товаров.
     *
     * @param orderId           идентификатор заказа
     * @param items             список товаров для отмены резервации
     * @param idempotencyKey    идемпотентный ключ для повторных запросов
     * @return true, если отмена успешна, иначе false
     */
    @CircuitBreaker(name = "inventoryClient", fallbackMethod = "fallbackCancel")
    @Retry(name = "inventoryClient", fallbackMethod = "fallbackCancel")
    public boolean cancelBatchReservation(String orderId, List<OrderItem> items, String idempotencyKey) {
        log.debug("Начало отмены резервации для заказа: orderId={}", orderId);

        validateOrderId(orderId);
        validateDomainItems(items);

        String effectiveIdempotencyKey = resolveIdempotencyKey(idempotencyKey);
        List<OrderItemRequest> grpcItems = orderMapper.toGrpcItemRequestList(items);

        CancelReservationRequest request = CancelReservationRequest.newBuilder()
                .setOrderId(orderId)
                .addAllItems(grpcItems)
                .setIdempotencyKey(effectiveIdempotencyKey)
                .build();

        return execute("cancel", orderId, () -> {
            CancelReservationResponse response = stubWithDeadline().cancelBatchReservation(request);
            return handleBusinessResponse(
                    "cancel",
                    orderId,
                    response.getSuccess(),
                    response.getError(),
                    response.getMessage()
            );
        });
    }

    /**
     * Фиксирует списание товаров после успешной оплаты.
     *
     * @param orderId           идентификатор заказа
     * @param idempotencyKey    идемпотентный ключ для повторных запросов
     * @return true, если фиксация успешна, иначе false
     */
    @CircuitBreaker(name = "inventoryClient", fallbackMethod = "fallbackCommit")
    public boolean commitBatchReduction(String orderId, String idempotencyKey) {
        log.debug("Начало фиксации списания для заказа: orderId={}", orderId);

        validateOrderId(orderId);
        String effectiveIdempotencyKey = resolveIdempotencyKey(idempotencyKey);

        CommitReductionRequest request = CommitReductionRequest.newBuilder()
                .setOrderId(orderId)
                .setIdempotencyKey(effectiveIdempotencyKey)
                .build();

        return execute("commit", orderId, () -> {
            CommitReductionResponse response = stubWithDeadline().commitBatchReduction(request);
            return handleBusinessResponse(
                    "commit",
                    orderId,
                    response.getSuccess(),
                    response.getError(),
                    response.getMessage()
            );
        });
    }

    private InventoryServiceGrpc.InventoryServiceBlockingStub stubWithDeadline() {
        log.trace("Создание stub с дедлайном {} мс", deadlineMs);
        return inventoryServiceStub.withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Выполняет операцию с инвентаризацией и собирает метрики.
     *
     * @param operation операция (reserve/cancel/commit)
     * @param orderId идентификатор заказа
     * @param action  выполняемое действие
     * @param <T>    тип возвращаемого результата
     * @return результат выполнения операции
     */
    private <T> T execute(String operation, String orderId, Supplier<T> action) {
        log.debug("Выполнение операции {} для заказа {}", operation, orderId);

        Timer.Sample sample = Timer.start(meterRegistry);
        String previousOperation = MDC.get("operation");
        String previousTraceId = MDC.get("traceId");
        String effectiveTraceId = StringUtils.hasText(previousTraceId)
                ? previousTraceId
                : UUID.randomUUID().toString();

        try {
            MDC.put("operation", operation);
            MDC.put("traceId", effectiveTraceId);

            T result = action.get();
            log.info("Операция инвентаризации завершена успешно: operation={}, orderId={}", operation, orderId);
            return result;
        } catch (StatusRuntimeException e) {
            grpcErrorCounter(operation, e.getStatus().getCode().name()).increment();
            log.error(
                    "gRPC-ошибка при выполнении операции инвентаризации: operation={}, orderId={}, status={}, description={}",
                    operation,
                    orderId,
                    e.getStatus().getCode(),
                    e.getStatus().getDescription(),
                    e
            );
            throw e;
        } catch (RuntimeException e) {
            internalErrorCounter(operation, e.getClass().getSimpleName()).increment();
            log.error(
                    "Неожиданная ошибка клиента инвентаризации: operation={}, orderId={}",
                    operation,
                    orderId,
                    e
            );
            throw e;
        } finally {
            sample.stop(durationTimer(operation));
            restoreMdc("operation", previousOperation);
            restoreMdc("traceId", previousTraceId);
            log.trace("Метрики операции {} собраны для заказа {}", operation, orderId);
        }
    }

    /**
     * Обрабатывает бизнес-ответ от сервиса инвентаризации.
     *
     * @param operation операция
     * @param orderId идентификатор заказа
     * @param success признак успешности операции
     * @param error   тип ошибки резервации
     * @param message сообщение об ошибке
     * @return true, если операция успешна, иначе false
     */
    private boolean handleBusinessResponse(
            String operation,
            String orderId,
            boolean success,
            ReservationError error,
            String message
    ) {
        if (!success) {
            requestCounter(operation, "business_failure", errorTag(error)).increment();
            log.warn(
                    "Операция инвентаризации не удалась: operation={}, orderId={}, error={}, message={}",
                    operation,
                    orderId,
                    errorTag(error),
                    StringUtils.hasText(message) ? message : "-"
            );
            return false;
        }

        requestCounter(operation, "success", "NONE").increment();
        log.info("Операция инвентаризации успешна: operation={}, orderId={}", operation, orderId);
        return true;
    }

    // --- Вспомогательные методы валидации ---

    private void validateOrderId(String orderId) {
        if (!StringUtils.hasText(orderId)) {
            log.error("Некорректный идентификатор заказа: orderId='{}'", orderId);
            throw new IllegalArgumentException("Идентификатор заказа не может быть пустым");
        }
    }

    private void validateDomainItems(List<OrderItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            log.error("Список товаров пуст для операции инвентаризации");
            throw new IllegalArgumentException("Список товаров не может быть пустым");
        }

        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            if (item == null) {
                log.error("Обнаружен null-элемент в списке товаров на позиции {}", i);
                throw new IllegalArgumentException("Элемент списка товаров не может быть null");
            }
            if (item.getProductId() == null) {
                log.error("Обнаружен товар без идентификатора продукта на позиции {}", i);
                throw new IllegalArgumentException("Идентификатор продукта не может быть null");
            }
            if (item.getQuantity() <= 0) {
                log.error("Недопустимое количество товара: quantity={} на позиции {}", item.getQuantity(), i);
                throw new IllegalArgumentException("Количество товара должно быть больше 0");
            }
        }
    }

    private String resolveIdempotencyKey(String idempotencyKey) {
        String effectiveKey = StringUtils.hasText(idempotencyKey)
                ? idempotencyKey
                : UUID.randomUUID().toString();

        log.debug("Используемый идемпотентный ключ: {}",
                StringUtils.hasText(idempotencyKey) ? "передан пользователем" : "сгенерирован автоматически");

        return effectiveKey;
    }

    // --- Работа с MDC (контекстом логирования) ---

    private void restoreMdc(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            log.trace("Удалено значение MDC для ключа: {}", key);
        } else {
            MDC.put(key, previousValue);
            log.trace("Восстановлено значение MDC для ключа {}: '{}'", key, previousValue);
        }
    }

    // --- Метрики ---

    private String errorTag(ReservationError error) {
        return error == null ? "UNKNOWN" : error.name();
    }

    private Counter requestCounter(String operation, String result, String error) {
        return meterRegistry.counter(
                METRIC_REQUESTS,
                "operation", operation,
                "result", result,
                "error", error
        );
    }

    private Counter grpcErrorCounter(String operation, String status) {
        return meterRegistry.counter(
                METRIC_GRPC_ERRORS,
                "operation", operation,
                "status", status
        );
    }

    private Counter internalErrorCounter(String operation, String type) {
        return meterRegistry.counter(
                METRIC_INTERNAL_ERRORS,
                "operation", operation,
                "type", type
        );
    }

    private Timer durationTimer(String operation) {
        return meterRegistry.timer(
                METRIC_DURATION,
                "operation", operation
        );
    }

    // --- Методы fallback для CircuitBreaker и Retry ---

    @SuppressWarnings("unused")
    private boolean fallbackReserve(String orderId, List<OrderItem> items, String idempotencyKey, Exception e) {
        log.error("Сработал fallback для reserve: orderId={}, ошибка={}", orderId, e.getMessage());
        return false;
    }

    @SuppressWarnings("unused")
    private boolean fallbackCancel(String orderId, List<OrderItem> items, String idempotencyKey, Exception e) {
        log.error("Сработал fallback для cancel: orderId={}, ошибка={}", orderId, e.getMessage());
        return false;
    }

    @SuppressWarnings("unused")
    private boolean fallbackCommit(String orderId, String idempotencyKey, Exception e) {
        log.error("Сработал fallback для commit: orderId={}, ошибка={}", orderId, e.getMessage());
        return false;
    }
}
