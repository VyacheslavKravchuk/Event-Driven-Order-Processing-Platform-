package com.inovexx.user_service.service.impl;

import com.inovexx.user_service.dto.WalletRequestDto;
import com.inovexx.user_service.entity.wallet.WalletRegistered;
import com.inovexx.user_service.entity.wallet.WalletRequest;
import com.inovexx.user_service.enums.Operation;
import com.inovexx.user_service.exception.IllegalArgumentWalletException;
import com.inovexx.user_service.exception.WalletRegisteredNotFoundException;
import com.inovexx.user_service.exception.WalletRequestNotFoundException;
import com.inovexx.user_service.repository.WalletRepository;
import com.inovexx.user_service.repository.WalletRequestRepository;
import com.inovexx.user_service.service.WalletRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletRequestServiceImpl implements WalletRequestService {

    private final WalletRepository walletRepository;
    private final WalletRequestRepository walletRequestRepository;
    // Если маппер нужен, его тоже нужно объявить как final
    // private final WalletRequestMapper walletRequestMapper;


    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public WalletRequestDto operationInputAndOutput(String walletId, WalletRequestDto walletRequestDto) {

        // 1. Валидация входных DTO и UUID
        if (walletRequestDto == null) {
            log.warn("Получен пустой запрос на транзакцию");
            throw new WalletRequestNotFoundException("Запрос отсутствует");
        }

        // Попытка распарсить UUID и найти кошелек
        UUID uuid;
        try {
            uuid = UUID.fromString(walletId);
        } catch (IllegalArgumentException e) {
            log.warn("Невалидный формат UUID: {}", walletId);
            throw new IllegalArgumentWalletException("Невалидный идентификатор кошелька");
        }

        WalletRegistered walletRegistered = walletRepository.findById(uuid)
                .orElseThrow(() -> {
                    log.warn("Кошелек не найден с ID: {}", walletId);
                    return new WalletRegisteredNotFoundException("Кошелек с идентификатором " + walletId + " не найден");
                });

        // 2. Логика операции с использованием BigDecimal
        BigDecimal balanceCurrent = walletRegistered.getBalance();
        Operation operation = walletRequestDto.operationType();
        BigDecimal amount = walletRequestDto.amount();

        if (operation == Operation.DEPOSIT) {
            // Используем метод add() для BigDecimal
            BigDecimal newBalance = balanceCurrent.add(amount);
            walletRegistered.setBalance(newBalance);
            log.info("Пополнение кошелька {}. Баланс изменен с {} на {}", walletId, balanceCurrent, newBalance);

        } else if (operation == Operation.WITHDRAW) {
            // Используем метод subtract() для BigDecimal
            BigDecimal newBalance = balanceCurrent.subtract(amount);

            // Сравнение баланса
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Недостаточно средств для снятия с кошелька {}. Попытка снять {}", walletId, amount);
                throw new IllegalArgumentWalletException("Недостаточно средств для снятия");
            }
            walletRegistered.setBalance(newBalance);
            log.info("Снятие с кошелька {}. Баланс изменен с {} на {}", walletId, balanceCurrent, newBalance);

        } else {
            log.warn("Неверно указан тип операции: {}", operation);
            throw new IllegalArgumentWalletException("Неверно указан тип операции");
        }

        // 3. Сохранение данных
        WalletRequest transactionLog = new WalletRequest();
        transactionLog.setWallet(walletRegistered);
        transactionLog.setAmount(amount);
        transactionLog.setOperationType(operation);

        WalletRequest savedTransaction = walletRequestRepository.save(transactionLog);

        return new WalletRequestDto(
                savedTransaction.getTransactionId(), // Передаем сгенерированный ID
                savedTransaction.getOperationType(),
                savedTransaction.getAmount(),
                savedTransaction.getCreatedAt(),
                savedTransaction.getWallet().getWalletId()
        );
    }
}
