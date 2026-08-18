package com.inovexx.inventory_service.service.grpc;

import com.inovexx.inventory.grpc.*;
import com.inovexx.inventory_service.config.InventoryMetrics;
import com.inovexx.inventory_service.entity.Inventory;
import com.inovexx.inventory_service.entity.ReservationLog;
import com.inovexx.inventory_service.repository.InventoryRepository;
import com.inovexx.inventory_service.repository.ReservationLogRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryRepository inventoryRepository;
    private final ReservationLogRepository reservationLogRepository;
    // Делаем final для автовайринга через @RequiredArgsConstructor
    private final InventoryMetrics inventoryMetrics;

    // Для идемпотентности (опционально, можно использовать Redis)
    private final Set<String> processedIdempotencyKeys = ConcurrentHashMap.newKeySet();

    // Статусы
    private enum ReservationStatus {
        RESERVED,  // Резерв (ожидает списания)
        CANCELLED, // Отменен (до списания)
        COMMITTED  // Списан (после успешной оплаты)
    }

    @Override
    @Timed("inventory.reserve.batch")
    @Transactional
    public void reserveBatchStock(ReserveBatchRequest request,
                                  StreamObserver<ReserveBatchResponse> responseObserver) {
        inventoryMetrics.incrementReserveRequests();
        Timer.Sample sample = inventoryMetrics.startTimer();
        String orderId = request.getOrderId();
        String idempotencyKey = request.getIdempotencyKey();
        log.info("[GRPC] Пакетный резерв: Старт для заказа №{} (позиций: {})", orderId, request.getItemsCount());

        try {
            if (!idempotencyKey.isEmpty()) {
                if (!processedIdempotencyKeys.add(idempotencyKey)) {
                    log.warn("[GRPC] Повторный запрос с idempotency_key={}", idempotencyKey);
                    inventoryMetrics.incrementReserveFailure(); // Метрика ошибки
                    responseObserver.onNext(ReserveBatchResponse.newBuilder()
                            .setSuccess(false)
                            .setError(ReservationError.IDEMPOTENCY_VIOLATION)
                            .setMessage("Запрос с таким idempotency_key уже обрабатывался")
                            .build());
                    responseObserver.onCompleted();
                    return;
                }
            }

            List<Inventory> lockedInventories = new ArrayList<>();

            for (OrderItemRequest item : request.getItemsList()) {
                String productId = item.getProductId();
                int quantity = item.getQuantity();

                Inventory inventory = inventoryRepository.findWithLockByProductId(productId)
                        .orElseThrow(() -> {
                            inventoryMetrics.incrementReserveFailure(); // Метрика ошибки
                            return Status.NOT_FOUND
                                    .withDescription("Товар " + productId + " не найден в каталоге склада")
                                    .asRuntimeException();
                        });

                if (inventory.getAvailableStock() < quantity) {
                    log.warn("[GRPC] Пакетный отказ: Недостаточно товара {} для заказа №{}. Надо: {}, Есть: {}",
                            productId, orderId, quantity, inventory.getAvailableStock());

                    inventoryMetrics.incrementReserveFailure(); // Метрика ошибки
                    responseObserver.onNext(buildResponse(false, "Недостаточно товара на складе: " + productId));
                    responseObserver.onCompleted();
                    return;
                }

                lockedInventories.add(inventory);
            }

            for (int i = 0; i < request.getItemsCount(); i++) {
                OrderItemRequest item = request.getItems(i);
                Inventory inventory = lockedInventories.get(i);
                int quantity = item.getQuantity();

                inventory.setAvailableStock(inventory.getAvailableStock() - quantity);
                inventory.setReservedStock(inventory.getReservedStock() + quantity);
                inventoryRepository.save(inventory);

                ReservationLog logEntry = ReservationLog.builder()
                        .orderId(orderId)
                        .productId(item.getProductId())
                        .status(ReservationStatus.RESERVED.name())
                        .quantity(quantity)
                        .timestamp(OffsetDateTime.now())
                        .build();
                reservationLogRepository.save(logEntry);
            }

            inventoryMetrics.incrementReserveSuccess(); // Метрика успешного завершения
            log.info("[GRPC] Пакетный резерв: Успешно заблокированы все товары для заказа №{}", orderId);
            responseObserver.onNext(buildResponse(true, "Все товары успешно зарезервированы"));
            responseObserver.onCompleted();

        } catch (Exception e) {
            inventoryMetrics.incrementReserveFailure(); // Метрика критической ошибки
            log.error("[GRPC] Критическая ошибка пакетного резерва для заказа №{}: {}", orderId, e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Внутренняя ошибка склада: " + e.getMessage())
                    .asRuntimeException());
        } finally {
            inventoryMetrics.stopTimer(sample); // Гарантированный замер длительности в любом сценарии
        }
    }


    // ОТМЕНА РЕЗЕРВАЦИИ (компенсирующая операция для Саги)
    @Override
    @Transactional
    public void cancelBatchReservation(CancelReservationRequest request,
                                       StreamObserver<CancelReservationResponse> responseObserver) {
        inventoryMetrics.incrementCancelRequests();
        Timer.Sample sample = inventoryMetrics.startTimer();
        String orderId = request.getOrderId();
        String idempotencyKey = request.getIdempotencyKey();
        log.info("[GRPC] Запрос на отмену резервации: заказ №{} (idempotency_key={})", orderId, idempotencyKey);

        String status = "SUCCESS";
        try {
            // Проверка идемпотентности
            if (!idempotencyKey.isEmpty() && !processedIdempotencyKeys.add(idempotencyKey)) {
                log.warn("[GRPC] Повторный запрос на отмену с idempotency_key={}", idempotencyKey);
                status = "IDEMPOTENCY_VIOLATION";
                inventoryMetrics.incrementCancelFailure(status);

                responseObserver.onNext(CancelReservationResponse.newBuilder()
                        .setSuccess(false)
                        .setError(ReservationError.IDEMPOTENCY_VIOLATION)
                        .setMessage("Запрос на отмену с таким idempotency_key уже обрабатывался")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            // Ищем все активные резервы для заказа
            List<ReservationLog> activeReservations = reservationLogRepository.findByOrderIdAndStatus(
                    orderId,
                    ReservationStatus.RESERVED.name()
            );

            if (activeReservations.isEmpty()) {
                log.warn("[GRPC] Нет активных резервов для отмены: заказ №{}", orderId);
                status = "NO_ACTIVE_RESERVATIONS";
                inventoryMetrics.incrementCancelFailure(status);

                responseObserver.onNext(CancelReservationResponse.newBuilder()
                        .setSuccess(false)
                        .setError(ReservationError.NO_ACTIVE_RESERVATIONS)
                        .setMessage("Нет активных резервов для отмены")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            // Отменяем резервы и возвращаем товар на склад
            for (ReservationLog logEntry : activeReservations) {
                Inventory inventory = inventoryRepository.findByProductId(logEntry.getProductId())
                        .orElseThrow(() -> Status.NOT_FOUND
                                .withDescription("Товар " + logEntry.getProductId() + " не найден в каталоге склада")
                                .asRuntimeException());

                // Возвращаем товар на склад
                inventory.setReservedStock(inventory.getReservedStock() - logEntry.getQuantity());
                inventory.setAvailableStock(inventory.getAvailableStock() + logEntry.getQuantity());
                inventoryRepository.save(inventory);

                // Обновляем статус лога
                logEntry.setStatus(ReservationStatus.CANCELLED.name());
                reservationLogRepository.save(logEntry);
            }

            inventoryMetrics.incrementCancelSuccess();
            log.info("[GRPC] Успешно отменены все резервы для заказа №{}", orderId);
            responseObserver.onNext(CancelReservationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Все резервы успешно отменены")
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            status = "ERROR";
            inventoryMetrics.incrementCancelError(e.getClass().getSimpleName());
            log.error("[GRPC] Ошибка отмены резервации для заказа №{}: {}", orderId, e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Ошибка отмены резервации: " + e.getMessage())
                    .asRuntimeException());
        } finally {
            inventoryMetrics.stopTimer(sample, "cancel", status);
        }
    }

    // ФИКСАЦИЯ СПИСАНИЯ (после успешной оплаты)
    @Override
    @Transactional
    public void commitBatchReduction(CommitReductionRequest request,
                                     StreamObserver<CommitReductionResponse> responseObserver) {
        inventoryMetrics.incrementCommitRequests();
        Timer.Sample sample = inventoryMetrics.startTimer();
        String orderId = request.getOrderId();
        String idempotencyKey = request.getIdempotencyKey();
        log.info("[GRPC] Запрос на фиксацию списания: заказ №{} (idempotency_key={})", orderId, idempotencyKey);

        String status = "SUCCESS";
        try {
            if (!idempotencyKey.isEmpty() && !processedIdempotencyKeys.add(idempotencyKey)) {
                log.warn("[GRPC] Повторный запрос на фиксацию с idempotency_key={}", idempotencyKey);
                status = "IDEMPOTENCY_VIOLATION";
                inventoryMetrics.incrementCommitFailure(status);

                responseObserver.onNext(CommitReductionResponse.newBuilder()
                        .setSuccess(false)
                        .setError(ReservationError.IDEMPOTENCY_VIOLATION)
                        .setMessage("Запрос на фиксацию с таким idempotency_key уже обрабатывался")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            // Ищем все активные резервы для заказа
            List<ReservationLog> activeReservations = reservationLogRepository.findByOrderIdAndStatus(
                    orderId,
                    ReservationStatus.RESERVED.name()
            );

            if (activeReservations.isEmpty()) {
                log.warn("[GRPC] Нет активных резервов для фиксации: заказ №{}", orderId);
                status = "NO_ACTIVE_RESERVATIONS";
                inventoryMetrics.incrementCommitFailure(status);

                responseObserver.onNext(CommitReductionResponse.newBuilder()
                        .setSuccess(false)
                        .setError(ReservationError.NO_ACTIVE_RESERVATIONS)
                        .setMessage("Нет активных резервов для фиксации")
                        .build());
                responseObserver.onCompleted();
                return;
            }

            // Фиксируем списание (убираем из reservedStock)
            for (ReservationLog logEntry : activeReservations) {
                Inventory inventory = inventoryRepository.findByProductId(logEntry.getProductId())
                        .orElseThrow(() -> Status.NOT_FOUND
                                .withDescription("Товар " + logEntry.getProductId() + " не найден в каталоге склада")
                                .asRuntimeException());

                inventory.setReservedStock(inventory.getReservedStock() - logEntry.getQuantity());
                inventoryRepository.save(inventory);

                // Обновляем статус лога
                logEntry.setStatus(ReservationStatus.COMMITTED.name());
                reservationLogRepository.save(logEntry);
            }

            inventoryMetrics.incrementCommitSuccess();
            log.info("[GRPC] Успешно зафиксировано списание для заказа №{}", orderId);
            responseObserver.onNext(CommitReductionResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Все товары успешно списаны")
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            status = "ERROR";
            inventoryMetrics.incrementCommitError(e.getClass().getSimpleName());
            log.error("[GRPC] Ошибка фиксации списания для заказа №{}: {}", orderId, e.getMessage(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Ошибка фиксации списания: " + e.getMessage())
                    .asRuntimeException());
        } finally {
            inventoryMetrics.stopTimer(sample, "commit", status);
        }
    }

    // Вспомогательный метод для построения ответов
    private ReserveBatchResponse buildResponse(boolean success, String message) {
        return ReserveBatchResponse.newBuilder()
                .setSuccess(success)
                .setMessage(message)
                .build();
    }
}
