package com.inovexx.user_service.service.grpc;

import com.inovexx.order_service.grpc.PaymentRequest;
import com.inovexx.order_service.grpc.PaymentResponse;
import com.inovexx.order_service.grpc.UserServiceGrpc;
import com.inovexx.user_service.entity.wallet.WalletRegistered;
import com.inovexx.user_service.enums.Operation;
import com.inovexx.user_service.repository.WalletRepository;
import com.inovexx.user_service.service.WalletRequestService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.UUID;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final WalletRequestService walletService;
    private final WalletRepository walletRepository; // Для поиска UUID по Long userId

    @Override
    public void deductBalance(PaymentRequest request, StreamObserver<PaymentResponse> responseObserver) {
        log.info("gRPC запрос: Списание средств для заказа #{}", request.getOrderId());
        processPayment(request, Operation.WITHDRAW, responseObserver);
    }

    @Override
    public void refundBalance(PaymentRequest request, StreamObserver<PaymentResponse> responseObserver) {
        log.info("gRPC запрос: Возврат средств для заказа #{}", request.getOrderId());
        processPayment(request, Operation.DEPOSIT, responseObserver);
    }

    /**
     * Общий метод для обработки платежных операций
     */
    private void processPayment(PaymentRequest request, Operation operation, StreamObserver<PaymentResponse> responseObserver) {
        try {
            // 1. Находим UUID кошелька по Long userId из запроса
            // Если в вашей системе userId и walletId это одно и то же,
            // используйте UUID.nameUUIDFromBytes(String.valueOf(request.getUserId()).getBytes())
            // или доработайте поиск:
            UUID walletUuid = walletRepository.findByUserId(request.getUserId())
                    .map(WalletRegistered::getWalletId)
                    .orElseThrow(() -> new RuntimeException("Кошелек не найден для пользователя: " + request.getUserId()));

            // 2. Вызываем бизнес-логику
            walletService.processOrderPayment(
                    request.getOrderId(),
                    walletUuid,
                    BigDecimal.valueOf(request.getAmount()),
                    operation
            );

            // 3. Успешный ответ
            responseObserver.onNext(PaymentResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Операция " + operation + " выполнена успешно")
                    .build());

        } catch (Exception e) {
            log.error("Ошибка при обработке платежа {}: {}", request.getOrderId(), e.getMessage());
            responseObserver.onNext(PaymentResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build());
        } finally {
            responseObserver.onCompleted();
        }
    }
}
