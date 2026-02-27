package com.inovexx.order_service.client;

import com.inovexx.order_service.exception.ProductNotFoundException;
import com.inovexx.order_service.grpc.CancelReserveRequest;
import com.inovexx.order_service.grpc.InventoryServiceGrpc;
import com.inovexx.order_service.grpc.ReserveRequest;
import com.inovexx.order_service.grpc.ReserveResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InventoryClient {

    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackReserveStock")
    public boolean reserveStock(String productId, int quantity) {
        ReserveRequest request = ReserveRequest.newBuilder()
                .setProductId(productId)
                .setQuantity(quantity)
                .build();

        try {
            ReserveResponse response = inventoryStub.reserveStock(request);
            return response.getSuccess();
        } catch (StatusRuntimeException e) {
            // Если сервер прислал NOT_FOUND, выбрасываем наше исключение
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ProductNotFoundException("Продукт не найден на складе: " + productId);
            }
            // В случае других ошибок (сеть, таймаут) логируем и возвращаем false
            log.error("Ошибка gRPC при резерве товара {}: {}", productId, e.getStatus());
            return false;
        }
    }

    // Этот метод вызовется автоматически, если inventory-service лежит или тормозит
    public boolean fallbackReserveStock(String productId, int quantity, Throwable t) {
        log.error("Circuit Breaker сработал! Склад недоступен. Причина: {}", t.getMessage());
        // Возвращаем false, чтобы order-service перевел заказ в CANCELLED или выдал ошибку пользователю
        return false;
    }

    public void compensateReservation(String productId, int quantity) {
        try {
            CancelReserveRequest request = CancelReserveRequest.newBuilder()
                    .setProductId(productId)
                    .setQuantity(quantity)
                    .build();
            inventoryStub.cancelReservation(request);
        } catch (Exception e) {
            log.error("КРИТИЧЕСКАЯ ОШИБКА: Не удалось выполнить компенсацию для товара {}", productId);
            // Здесь можно отправить событие в специальный топик "manual-intervention-needed"
        }
    }
}

