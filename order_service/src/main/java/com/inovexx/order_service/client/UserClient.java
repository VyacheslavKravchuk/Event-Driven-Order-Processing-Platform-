package com.inovexx.order_service.client;

import com.inovexx.order_service.grpc.PaymentRequest;
import com.inovexx.order_service.grpc.PaymentResponse;
import com.inovexx.order_service.grpc.UserServiceGrpc;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class UserClient {

    @GrpcClient("user-service") // Имя из вашего application.properties
    private UserServiceGrpc.UserServiceBlockingStub userStub;

    /**
     * Списание средств (Withdraw)
     */
    @Retry(name = "userService")
    public boolean deductBalance(Long userId, BigDecimal amount, Long orderId) {
        log.info("Отправка gRPC запроса на списание: User {}, Сумма {}, Заказ {}", userId, amount, orderId);

        PaymentRequest request = PaymentRequest.newBuilder()
                .setUserId(userId)
                .setAmount(amount.doubleValue())
                .setOrderId(orderId)
                .build();

        try {
            PaymentResponse response = userStub.deductBalance(request);
            if (!response.getSuccess()) {
                log.warn("Оплата отклонена: {}", response.getMessage());
            }
            return response.getSuccess();
        } catch (Exception e) {
            log.error("Критическая ошибка gRPC при оплате заказа {}: {}", orderId, e.getMessage());
            return false;
        }
    }

    /**
     * Возврат средств (Refund / Компенсация Саги)
     */
    @Retry(name = "userService")
    public void refundBalance(Long userId, BigDecimal amount, Long orderId) {
        log.info("Отправка gRPC запроса на ВОЗВРАТ: User {}, Сумма {}, Заказ {}", userId, amount, orderId);

        PaymentRequest request = PaymentRequest.newBuilder()
                .setUserId(userId)
                .setAmount(amount.doubleValue())
                .setOrderId(orderId)
                .build();

        try {
            PaymentResponse response = userStub.refundBalance(request);
            if (!response.getSuccess()) {
                log.error("НЕ УДАЛОСЬ ВЕРНУТЬ ДЕНЬГИ для заказа {}: {}", orderId, response.getMessage());
            }
        } catch (Exception e) {
            log.error("ФАТАЛЬНАЯ ОШИБКА gRPC при возврате денег {}: {}", orderId, e.getMessage());
        }
    }
}