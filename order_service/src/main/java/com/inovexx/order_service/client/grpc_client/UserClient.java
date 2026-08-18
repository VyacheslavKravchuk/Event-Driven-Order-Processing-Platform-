package com.inovexx.order_service.client.grpc_client;

import com.inovexx.order_service.grpc.*;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class UserClient {

    // Это и есть наш "BlockingStub" - он генерируется gRPC из proto файла
    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userStub;

    public String getUserEmail(Long userId) {
        log.info("[GRPC] Запрос email для пользователя #{}", userId);
        try {
            UserRequest request = UserRequest.newBuilder()
                    .setUserId(userId)
                    .build();

            // Синхронный вызов
            UserResponse response = userStub.getUserProfile(request);

            String email = response.getEmail();
            if (email == null || email.isBlank()) {
                throw new RuntimeException("Email пользователя пуст");
            }
            return email;
        } catch (StatusRuntimeException e) {
            log.error("[GRPC] Ошибка профиля пользователя #{}: {}", userId, e.getStatus());
            throw new RuntimeException("Сервис пользователей недоступен или данные не найдены", e);
        }
    }

    @Retry(name = "userService")
    public boolean deductBalance(Long userId, BigDecimal amount, String orderId) {
        log.info("[GRPC] Списание баланса: User {}, Сумма {}, Заказ {}", userId, amount, orderId);

        PaymentRequest request = createPaymentRequest(userId, amount, orderId);

        try {
            PaymentResponse response = userStub.deductBalance(request);
            if (!response.getSuccess()) {
                log.warn("[GRPC] Списание отклонено для заказа #{}: {}", orderId, response.getMessage());
            }
            return response.getSuccess();
        } catch (StatusRuntimeException e) {
            log.error("[GRPC] Ошибка при списании для заказа #{}: {}", orderId, e.getStatus());
            // Пробрасываем исключение, чтобы @Retry сработал
            throw e;
        }
    }

    @Retry(name = "userService")
    public boolean refundBalance(Long userId, BigDecimal amount, String orderId) {
        log.info("[GRPC] Возврат баланса: User {}, Сумма {}, Заказ {}", userId, amount, orderId);

        PaymentRequest request = createPaymentRequest(userId, amount, orderId);

        try {
            // ИСПРАВЛЕНО: используем правильный stub (userStub)
            PaymentResponse response = userStub.refundBalance(request);

            if (!response.getSuccess()) {
                log.error("[GRPC] Сервер отказал в возврате для заказа #{}: {}", orderId, response.getMessage());
                // Если бизнес-логика говорит "нет", ретрай обычно не поможет, возвращаем false
                return false;
            }

            log.info("[GRPC] Успешный возврат для заказа #{}", orderId);
            return true;
        } catch (StatusRuntimeException e) {
            log.error("[GRPC] Сетевая ошибка при возврате для заказа #{}: {}", orderId, e.getStatus());
            // Пробрасываем ошибку для срабатывания @Retry (если это таймаут или 503)
            throw e;
        }
    }

    // Выносим создание реквеста, чтобы не дублировать код
    private PaymentRequest createPaymentRequest(Long userId, BigDecimal amount, String orderId) {
        return PaymentRequest.newBuilder()
                .setUserId(userId)
                .setAmount(amount.toPlainString()) // Используем toPlainString для точности
                .setOrderId(orderId)
                .build();
    }
}