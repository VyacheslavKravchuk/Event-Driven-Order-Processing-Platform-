package com.inovexx.user_service.service.impl;

import com.inovexx.user_service.dto.WalletRequestDto;
import com.inovexx.user_service.entity.wallet.WalletRegistered;
import com.inovexx.user_service.entity.wallet.WalletRequest;
import com.inovexx.user_service.enums.Operation;
import com.inovexx.user_service.exception.IllegalArgumentWalletException;
import com.inovexx.user_service.exception.WalletRegisteredNotFoundException;
import com.inovexx.user_service.exception.WalletRequestNotFoundException;
import com.inovexx.user_service.mapper.WalletRequestMapper;
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
    private final WalletRequestMapper walletRequestMapper; // Внедряем маппер

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public WalletRequestDto operationInputAndOutput(String walletId, WalletRequestDto walletRequestDto) {

        // 1. Валидация входных данных
        if (walletRequestDto == null) {
            log.warn("Получен пустой запрос на транзакцию");
            throw new WalletRequestNotFoundException("Запрос отсутствует");
        }

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

        // 2. Логика изменения баланса
        updateBalance(walletRegistered, walletRequestDto);

        // 3. Сохранение транзакции и маппинг
        // Используем маппер для создания сущности из DTO
        WalletRequest transactionLog = walletRequestMapper.toEntity(walletRequestDto);

        // Устанавливаем связь с кошельком вручную, так как это бизнес-логика
        transactionLog.setWallet(walletRegistered);

        WalletRequest savedTransaction = walletRequestRepository.save(transactionLog);

        log.info("Транзакция {} сохранена для кошелька {}", savedTransaction.getTransactionId(), walletId);

        // Используем маппер для формирования ответа
        return walletRequestMapper.toDto(savedTransaction);
    }

    /**
     * Вспомогательный метод для обработки бизнес-логики изменения баланса.
     * Вынесен отдельно для улучшения читаемости основного метода.
     */
    private void updateBalance(WalletRegistered wallet, WalletRequestDto dto) {
        BigDecimal balanceCurrent = wallet.getBalance();
        BigDecimal amount = dto.amount();
        Operation operation = dto.operationType();

        if (operation == Operation.DEPOSIT) {
            BigDecimal newBalance = balanceCurrent.add(amount);
            wallet.setBalance(newBalance);
            log.info("Пополнение кошелька {}. Баланс: {} -> {}", wallet.getWalletId(), balanceCurrent, newBalance);

        } else if (operation == Operation.WITHDRAW) {
            if (balanceCurrent.compareTo(amount) < 0) {
                log.warn("Недостаточно средств на кошельке {}. Баланс: {}, запрос: {}",
                        wallet.getWalletId(), balanceCurrent, amount);
                throw new IllegalArgumentWalletException("Недостаточно средств для снятия");
            }
            BigDecimal newBalance = balanceCurrent.subtract(amount);
            wallet.setBalance(newBalance);
            log.info("Снятие с кошелька {}. Баланс: {} -> {}", wallet.getWalletId(), balanceCurrent, newBalance);

        } else {
            log.warn("Неизвестный тип операции: {}", operation);
            throw new IllegalArgumentWalletException("Неверно указан тип операции");
        }
    }
}