package com.inovexx.user_service.service.grpc;

import com.google.protobuf.Timestamp;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@GrpcService
@Slf4j
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {
    private final WalletRequestService walletService;
    private final WalletRepository walletRepository;
    @Transactional
    @Override
    public void deductBalance(PaymentRequest request, StreamObserver<PaymentResponse> responseObserver) {
        log.info("gRPC запрос: Списание {} {} для заказа #{}", request.getAmount(), request.getCurrency(), request.getOrderId());
        processPayment(request, Operation.WITHDRAW, responseObserver);
    }
    @Transactional
    @Override
    public void refundBalance(PaymentRequest request, StreamObserver<PaymentResponse> responseObserver) {
        log.info("gRPC запрос: Возврат {} {} для заказа #{}", request.getAmount(), request.getCurrency(), request.getOrderId());
        processPayment(request, Operation.DEPOSIT, responseObserver);
    }
    /**
     * Общий метод для обработки платежных операций согласно user.proto
     */
    private void processPayment(PaymentRequest request, Operation operation, StreamObserver<PaymentResponse> responseObserver) {
        try {
            // 1. Поиск кошелька
            WalletRegistered wallet = walletRepository.findByUserId(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("Кошелек не найден для пользователя: " + request.getUserId()));
            // 2. Преобразование суммы (String -> BigDecimal)
            // Используем конструктор строки, это самый точный способ для BigDecimal
            BigDecimal amount = new BigDecimal(request.getAmount());
            // 3. Вызов бизнес-логики (предполагаем, что сервис возвращает обновленный баланс или сущность)
            // Если ваш сервис пока не возвращает баланс, можно вызвать walletRepository.findById(wallet.getWalletId()) после операции
            walletService.processOrderPayment(
                    request.getOrderId(),
                    wallet.getWalletId(),
                    amount,
                    operation
            );
            // Получаем актуальный баланс после транзакции
            BigDecimal updatedBalance = walletRepository.findById(wallet.getWalletId())
                    .map(WalletRegistered::getBalance) // Предполагаем наличие поля balance в сущности
                    .orElse(BigDecimal.ZERO);
            // 4. Формирование успешного ответа согласно proto-контракту
            PaymentResponse response = PaymentResponse.newBuilder()
                    .setSuccess(true)
                    .setTransactionId(UUID.randomUUID().toString()) // Генерация ID транзакции
                    .setMessage("Операция " + operation + " выполнена")
                    .setNewBalance(updatedBalance.toPlainString()) // Превращаем BigDecimal обратно в String
                    .setCurrency(request.getCurrency())
                    .setProcessedAt(getCurrentTimestamp()) // Заполняем google.protobuf.Timestamp
                    .build();
            responseObserver.onNext(response);
        } catch (Exception e) {
            log.error("Ошибка при {} для заказа {}: {}", operation, request.getOrderId(), e.getMessage());
            // Ответ при ошибке (согласно контракту, success = false)
            responseObserver.onNext(PaymentResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Ошибка: " + e.getMessage())
                    .setProcessedAt(getCurrentTimestamp())
                    .build());
        } finally {
            responseObserver.onCompleted();
        }
    }
    /**
     * Вспомогательный метод для создания gRPC Timestamp из текущего времени
     */
    private Timestamp getCurrentTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }
}
