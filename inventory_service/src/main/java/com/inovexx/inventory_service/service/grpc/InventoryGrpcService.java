package com.inovexx.inventory_service.service.grpc;

import com.inovexx.inventory_service.repository.InventoryRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import com.inovexx.inventory_service.grpc.InventoryServiceGrpc;
import com.inovexx.inventory_service.grpc.ReserveRequest;
import com.inovexx.inventory_service.grpc.ReserveResponse;

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

        // 1. Ищем товар. Если его нет — выбрасываем NOT_FOUND в gRPC канал
        var inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Товар с ID " + productId
                                + " не существует в каталоге склада")
                        .asRuntimeException());

        // 2. Проверяем наличие нужного количества
        boolean hasEnough = inventory.getAvailableStock() >= quantity;

        if (hasEnough) {
            // Резервируем: уменьшаем доступный, увеличиваем зарезервированный
            inventory.setAvailableStock(inventory.getAvailableStock() - quantity);
            inventory.setReservedStock(inventory.getReservedStock() + quantity);
            inventoryRepository.save(inventory);
        }

        // 3. Формируем успешный ответ
        ReserveResponse response = ReserveResponse.newBuilder()
                .setSuccess(hasEnough)
                .setMessage(hasEnough ? "Резерв подтвержден" : "Недостаточно товара на складе")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
