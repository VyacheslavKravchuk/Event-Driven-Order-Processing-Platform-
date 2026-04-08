package com.inovexx.inventory_service.service.grpc;

import com.inovexx.inventory_service.grpc.*;
import com.inovexx.inventory_service.repository.InventoryRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryRepository inventoryRepository;

    @Override
    @Transactional
    public void reserveStock(ReserveRequest request,
                             StreamObserver<ReserveResponse> responseObserver) {
        String productId = request.getProductId();
        int quantity = request.getQuantity();

        log.info("[GRPC] Запрос на резервирование: Товар ID={}, Количество={}", productId, quantity);

        try {
            // 1. Пытаемся найти товар с блокировкой строки (Select for update)
            var inventory = inventoryRepository.findWithLockByProductId(productId)
                    .orElseThrow(() -> {
                        log.error("[GRPC] Ошибка: Товар {} не найден в базе склада", productId);
                        return Status.NOT_FOUND
                                .withDescription("Товар не найден в каталоге склада")
                                .asRuntimeException();
                    });

            log.debug("[GRPC] Товар найден. Текущий остаток: {}, Резерв: {}",
                    inventory.getAvailableStock(), inventory.getReservedStock());

            // 2. Проверяем наличие
            boolean hasEnough = inventory.getAvailableStock() >= quantity;
            String message;

            if (hasEnough) {
                // Выполняем резервирование
                inventory.setAvailableStock(inventory.getAvailableStock() - quantity);
                inventory.setReservedStock(inventory.getReservedStock() + quantity);
                inventoryRepository.save(inventory);

                message = "Резерв подтвержден успешно";
                log.info("[GRPC] Успех: Зарезервировано {} ед. товара {}. Новый остаток: {}",
                        quantity, productId, inventory.getAvailableStock());
            } else {
                message = "Недостаточно товара на складе (в наличии: " + inventory.getAvailableStock() + ")";
                log.warn("[GRPC] Отказ: Недостаточно товара {}. Запрошено: {}, В наличии: {}",
                        productId, quantity, inventory.getAvailableStock());
            }

            // 3. Формируем и отправляем ответ
            ReserveResponse response = ReserveResponse.newBuilder()
                    .setSuccess(hasEnough)
                    .setMessage(message)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("[GRPC] Критическая ошибка при обработке резерва для товара {}: {}",
                    productId, e.getMessage(), e);

            // Важно отправить ошибку в канал, чтобы клиент (OrderService) не ждал таймаута
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Внутренняя ошибка склада: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Метод компенсации (отмена резерва), если оплата заказа не прошла.
     */
    @Override
    public void cancelReservation(CancelReserveRequest request,
                                  StreamObserver<CancelReserveResponse> responseObserver) {
        String productId = request.getProductId();
        int quantity = request.getQuantity();

        log.info("[COMPENSATION] Запрос на отмену резерва: Товар ID={}, Количество={}", productId, quantity);

        try {
            var inventory = inventoryRepository.findWithLockByProductId(productId)
                    .orElseThrow(() -> new EntityNotFoundException("Товар не найден при компенсации"));

            // Возвращаем из резерва в доступные
            inventory.setReservedStock(inventory.getReservedStock() - quantity);
            inventory.setAvailableStock(inventory.getAvailableStock() + quantity);
            inventoryRepository.save(inventory);

            log.info("[COMPENSATION] Успех: Резерв для товара {} отменен. {} ед. вернулись в продажу.",
                    productId, quantity);

            responseObserver.onNext(CancelReserveResponse.newBuilder().setSuccess(true).build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("[COMPENSATION] ОШИБКА: Не удалось вернуть товар {} на склад! Причина: {}",
                    productId, e.getMessage());
            responseObserver.onError(Status.INTERNAL.asRuntimeException());
        }
    }
}
