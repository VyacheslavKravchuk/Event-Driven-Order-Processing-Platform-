package com.inovexx.order_service.client;

import com.inovexx.order_service.grpc.PaymentRequest;
import com.inovexx.order_service.grpc.PaymentResponse;
import com.inovexx.order_service.grpc.UserServiceGrpc;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class UserClient {
    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userStub;
    @Retry(name = "userService")
    public boolean deductBalance(Long userId, BigDecimal amount, Long orderId) {
        log.info("Запрос на списание: User {}, Сумма {}, Заказ {}", userId, amount, orderId);
        // Используем toPlainString() для сохранения точности BigDecimal
        PaymentRequest request = PaymentRequest.newBuilder()
                .setUserId(userId)
                .setAmount(amount.toPlainString())
                .setOrderId(orderId)
                .build();
        try {
            PaymentResponse response = userStub.deductBalance(request);
            if (!response.getSuccess()) {
                log.warn("Оплата отклонена: {}", response.getMessage());
            }
            return response.getSuccess();
        } catch (StatusRuntimeException e) {
            log.error("Ошибка gRPC (Статус: {}): {}", e.getStatus().getCode(), e.getMessage());
            // Пробрасываем для Retry, если это сетевая ошибка
            throw e;
        }
    }
    @Retry(name = "userService")
    public void refundBalance(Long userId, BigDecimal amount, Long orderId) {
        PaymentRequest request = PaymentRequest.newBuilder()
                .setUserId(userId)
                .setAmount(amount.toPlainString())
                .setOrderId(orderId)
                .build();
        try {
            PaymentResponse response = userStub.refundBalance(request);
            if (!response.getSuccess()) {
                // В Saga здесь важно выбросить исключение, чтобы транзакция не считалась закрытой
                throw new RuntimeException("Ошибка возврата: " + response.getMessage());
            }
        } catch (Exception e) {
            log.error("Критический сбой компенсации для заказа {}", orderId);
            throw e; // Обязательно для механизмов переповтора Saga
        }
    }
}