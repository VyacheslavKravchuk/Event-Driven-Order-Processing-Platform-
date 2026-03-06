package com.inovexx.user_service.service.impl;

import com.inovexx.user_service.dto.WalletOperationRequest;
import com.inovexx.user_service.dto.WalletRequestDto;
import com.inovexx.user_service.entity.wallet.WalletRegistered;
import com.inovexx.user_service.entity.wallet.WalletTransaction;
import com.inovexx.user_service.enums.Operation;
import com.inovexx.user_service.exception.IllegalArgumentWalletException;
import com.inovexx.user_service.exception.InsufficientFundsException;
import com.inovexx.user_service.exception.WalletRegisteredNotFoundException;
import com.inovexx.user_service.mapper.WalletRequestMapper;
import com.inovexx.user_service.repository.WalletRepository;
import com.inovexx.user_service.repository.WalletRequestRepository;
import com.inovexx.user_service.service.WalletRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletRequestServiceImpl implements WalletRequestService {

    private final WalletRepository walletRepository;
    private final WalletRequestRepository walletRequestRepository;
    private final WalletRequestMapper walletTransactionMapper;

    /**
     * Метод для простых операций (DEPOSIT/WITHDRAW) через API.
     */
    @Transactional
    @Override
    public WalletOperationRequest operationInputAndOutput(String walletIdStr,
                                                          WalletOperationRequest requestDto) {
        // 1. Валидация UUID
        UUID walletId = parseUuid(walletIdStr);

        // 2. Поиск кошелька
        WalletRegistered wallet = findWalletOrThrow(walletId);

        // 3. Бизнес-логика изменения баланса
        updateBalance(wallet, requestDto.amount(), requestDto.operationType());

        // 4. Маппинг и сохранение транзакции
        WalletTransaction transaction = walletTransactionMapper.fromOperationDtoToEntity(requestDto);
        transaction.setWallet(wallet); // Привязываем кошелек

        WalletTransaction saved = walletRequestRepository.save(transaction);
        log.info("Операция {} на сумму {} для кошелька {} успешно выполнена",
                requestDto.operationType(), requestDto.amount(), walletId);

        return walletTransactionMapper.toOperationDto(saved);
    }

    /**
     * Метод для процессинга заказов (Saga/Payments).
     */
    @Transactional // По умолчанию READ_COMMITTED достаточно при использовании Lock
    @Override
    public WalletRequestDto processOrderPayment(Long orderId, UUID walletId,
                                                BigDecimal amount, Operation operation) {
        // 1. ПРОВЕРКА ИДЕМПОТЕНТНОСТИ (Без блокировок, просто поиск)
        var existingOpt = walletRequestRepository.findByOrderIdAndOperationType(orderId, operation);
        if (existingOpt.isPresent()) {
            log.info("Повторный запрос для заказа {}. Возвращаем существующий результат.", orderId);
            return walletTransactionMapper.toFullDto(existingOpt.get());
        }
        // 2. БЛОКИРОВКА КОШЕЛЬКА (SELECT FOR UPDATE)
        // Теперь никто другой не сможет изменить этот баланс, пока мы не закончим
        WalletRegistered wallet = walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new WalletRegisteredNotFoundException("Кошелек не найден: " + walletId));
        // 3. ОБНОВЛЕНИЕ БАЛАНСА
        updateBalance(wallet, amount, operation);
        // 4. СОХРАНЕНИЕ ТРАНЗАКЦИИ
        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .orderId(orderId)
                .amount(amount)
                .operationType(operation)
                .createdAt(LocalDateTime.now()) // Не забываем время
                .build();
        // Сначала сохраняем кошелек (Dirty Checking сработает сам, но можно и явно)
        // Затем сохраняем запись о транзакции
        WalletTransaction saved = walletRequestRepository.save(transaction);

        log.info("Платеж по заказу {} на сумму {} успешно обработан. Новый баланс: {}",
                orderId, amount, wallet.getBalance());
        return walletTransactionMapper.toFullDto(saved);
    }
    private void updateBalance(WalletRegistered wallet, BigDecimal amount, Operation operation) {
        // Проверка на отрицательные суммы
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentWalletException("Сумма операции должна быть положительной");
        }
        BigDecimal currentBalance = wallet.getBalance();

        switch (operation) {
            case DEPOSIT:
                wallet.setBalance(currentBalance.add(amount));
                break;
            case WITHDRAW:
            case PAYMENT:
                if (currentBalance.compareTo(amount) < 0) {
                    throw new InsufficientFundsException("Недостаточно средств. Баланс: " + currentBalance);
                }
                wallet.setBalance(currentBalance.subtract(amount));
                break;
            default:
                throw new IllegalArgumentWalletException("Неподдерживаемый тип операции: " + operation);
        }
    }

    // --- Вспомогательные методы для чистоты кода ---

    private UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentWalletException("Невалидный формат UUID кошелька");
        }
    }

    private WalletRegistered findWalletOrThrow(UUID id) {
        return walletRepository.findById(id)
                .orElseThrow(() -> new WalletRegisteredNotFoundException("Кошелек не найден: " + id));
    }
}