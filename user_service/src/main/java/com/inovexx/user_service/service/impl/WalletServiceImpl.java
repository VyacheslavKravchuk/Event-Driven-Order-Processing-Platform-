package com.inovexx.user_service.service.impl;

import com.inovexx.user_service.dto.WalletDto;
import com.inovexx.user_service.dto.WalletRegisteredRequest;
import com.inovexx.user_service.entity.wallet.WalletRegistered;
import com.inovexx.user_service.exception.IllegalArgumentWalletException;
import com.inovexx.user_service.exception.WalletRegisteredNotFoundException;
import com.inovexx.user_service.mapper.WalletMapper;
import com.inovexx.user_service.repository.WalletRepository;
import com.inovexx.user_service.service.WalletService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Pattern;


@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true) // По умолчанию только чтение
public class WalletServiceImpl implements WalletService {

    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);

    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;

    @Override
    @Transactional // Для записи данных
    public WalletDto createWallet(WalletDto walletDto) {
        if (!isValidEmail(walletDto.email())) {
            log.warn("Попытка регистрации с неверным форматом email: {}", walletDto.email());
            throw new IllegalArgumentWalletException("Некорректный формат email");
        }

        // Проверка на дубликат
        if (walletRepository.findByEmail(walletDto.email()).isPresent()) {
            throw new IllegalArgumentWalletException("Кошелек с таким email уже существует");
        }

        WalletRegistered wallet = new WalletRegistered();
        walletMapper.updateWalletFromDto(walletDto, wallet);

        // Баланс при создании всегда 0, если иное не предусмотрено логикой
        wallet.setBalance(BigDecimal.ZERO);

        WalletRegistered saved = walletRepository.save(wallet);
        log.info("Создан новый кошелек с ID: {}", saved.getWalletId());

        return walletMapper.walletToWalletDto(saved);
    }

    public WalletRegisteredRequest findByEmailWallet(String email) {
        if (!isValidEmail(email)) {
            throw new IllegalArgumentWalletException("Некорректный формат данных");
        }

        return walletRepository.findByEmail(email)
                .map(w -> new WalletRegisteredRequest(w.getUser().getEmail(), w.getBalance()))
                .orElseThrow(() -> new WalletRegisteredNotFoundException("Кошелек не найден для: " + email));
    }

    @Override
    public WalletDto getWalletById(String walletId) {
        UUID uuid = parseUuid(walletId);

        return walletRepository.findById(uuid)
                .map(walletMapper::walletToWalletDto)
                .orElseThrow(() -> new WalletRegisteredNotFoundException("Кошелек с ID " + walletId + " не найден"));
    }

    @Override
    public BigDecimal operationGetBalance(String id) {
        UUID uuid = parseUuid(id);

        WalletRegistered wallet = walletRepository.findById(uuid)
                .orElseThrow(() -> new WalletRegisteredNotFoundException("Кошелек не найден"));

        log.info("Запрошен баланс для ID {}: {}", id, wallet.getBalance());
        return wallet.getBalance();
    }

    @Override
    public boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    // Вспомогательный метод для парсинга UUID
    private UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Ошибка парсинга UUID: {}", id);
            throw new IllegalArgumentWalletException("Невалидный формат идентификатора UUID");
        }
    }
}

