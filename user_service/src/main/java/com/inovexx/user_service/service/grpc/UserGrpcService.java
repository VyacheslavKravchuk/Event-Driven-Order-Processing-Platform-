package com.inovexx.user_service.service.grpc;

import com.google.protobuf.Timestamp;
import com.inovexx.order_service.grpc.*;
import com.inovexx.user_service.entity.User;
import com.inovexx.user_service.entity.wallet.WalletRegistered;
import com.inovexx.user_service.enums.Operation;
import com.inovexx.user_service.exception.UserNotFoundException;
import com.inovexx.user_service.repository.UserRepository;
import com.inovexx.user_service.repository.WalletRepository;
import com.inovexx.user_service.service.WalletRequestService;
import io.grpc.Status;
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

    // Добавляем репозиторий пользователей для получения email
    private final UserRepository userRepository;
    /**
     * Получение профиля пользователя (email, имя и т.д.)
     */
    @Override
    public void getUserProfile(UserRequest request, StreamObserver<UserResponse> responseObserver) {
        long userId = request.getUserId();
        log.info("[GRPC] Запрос профиля для пользователя #{}", userId);
        try {
            // 1. Ищем пользователя в БД
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new UserNotFoundException("Пользователь не найден: " + userId));
            // 2. Строим gRPC ответ
            UserResponse response = UserResponse.newBuilder()
                    .setUserId(user.getId())
                    .setEmail(user.getEmail())
                    .setFirstName(user.getFirstName() != null ? user.getFirstName() : "")
                    .setLastName(user.getLastName() != null ? user.getLastName() : "")
                    .setPhone(user.getPhoneNumber() != null ? user.getPhoneNumber() : "")
                    .setCreatedAt(com.google.protobuf.Timestamp.newBuilder()
                            .build())
                    .build();
            // 3. Отправляем ответ
            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.debug("[GRPC] Профиль пользователя #{} успешно отправлен", userId);
        } catch (UserNotFoundException e) {
            log.warn("[GRPC] Пользователь #{} не найден", userId);
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("[GRPC] Ошибка при получении профиля пользователя #{}: {}", userId, e.getMessage());
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Внутренняя ошибка сервера")
                    .asRuntimeException());
        }
    }

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
