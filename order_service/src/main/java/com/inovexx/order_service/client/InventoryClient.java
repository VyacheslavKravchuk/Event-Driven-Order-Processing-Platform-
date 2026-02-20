package com.inovexx.order_service.client;

import com.inovexx.order_service.grpc.InventoryServiceGrpc;
import com.inovexx.order_service.grpc.ReserveRequest;
import com.inovexx.order_service.grpc.ReserveResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public class InventoryClient {

    // Имя "inventory-service" должно совпадать с ключом в application.properties
    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    public boolean reserveStock(String productId, int quantity) {
        // Создаем запрос через билдер сгенерированного класса
        ReserveRequest request = ReserveRequest.newBuilder()
                .setProductId(productId)
                .setQuantity(quantity)
                .build();

        try {
            ReserveResponse response = inventoryStub.reserveStock(request);
            return response.getSuccess();
        } catch (Exception e) {
            System.err.println("CRITICAL: Не удалось связаться с Inventory Service: " + e.getMessage());
            return false;
        }
    }
}
