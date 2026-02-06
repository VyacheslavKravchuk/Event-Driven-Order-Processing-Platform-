package com.inovexx.user_service.service.impl;

import com.inovexx.user_service.dto.WalletDto;
import com.inovexx.user_service.dto.WalletRegisteredRequest;
import com.inovexx.user_service.entity.User;
import com.inovexx.user_service.entity.wallet.WalletRegistered;
import com.inovexx.user_service.exception.IllegalArgumentWalletException;
import com.inovexx.user_service.exception.WalletRegisteredNotFoundException;
import com.inovexx.user_service.mapper.WalletMapper;
import com.inovexx.user_service.repository.UserRepository;
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
    private final UserRepository userRepository;

    @Override
    @Transactional // Для записи данных в БД
    public WalletDto createWallet(WalletDto walletDto) {
        log.info("Начало процесса создания кошелька для email: {}", walletDto.email());

        // 1. Валидация формата email
        if (!isValidEmail(walletDto.email())) {
            log.warn("Некорректный формат email при регистрации: {}", walletDto.email());
            throw new IllegalArgumentWalletException("Некорректный формат email");
        }

        // 2. Проверка: не занят ли этот email другим кошельком
        if (walletRepository.findByEmail(walletDto.email()).isPresent()) {
            log.warn("Попытка создать дубликат кошелька для email: {}", walletDto.email());
            throw new IllegalArgumentWalletException("Кошелек с таким email уже существует");
        }

        // 3. Находим пользователя в базе (обязательно для установки связи)
        // Предполагается, что у вас есть userRepository. Если нет - добавьте его в конструктор
        User user = userRepository.findByEmail(walletDto.email())
                .orElseThrow(() -> {
                    log.error("Пользователь с email {} не найден в системе", walletDto.email());
                    return new WalletRegisteredNotFoundException("Пользователь для привязки кошелька не найден");
                });

        // 4. Создание и инициализация сущности
        WalletRegistered wallet = new WalletRegistered();

        // Маппим данные из DTO (имя, фамилия и т.д., если они есть в DTO)
        walletMapper.updateWalletFromDto(walletDto, wallet);

        // Устанавливаем связи и значения по умолчанию вручную
        wallet.setUser(user);
        wallet.setEmail(user.getEmail()); // Синхронизируем email в кошельке с email пользователя
        wallet.setBalance(BigDecimal.ZERO);

        // 5. Сохранение
        WalletRegistered saved = walletRepository.save(wallet);
        log.info("Кошелек успешно создан. WalletID: {}, UserID: {}", saved.getWalletId(), user.getId());

        return walletMapper.walletToWalletDto(saved);
    }

    public WalletRegisteredRequest findByEmailWallet(String email) {
        if (!isValidEmail(email)) {
            log.warn("Невалидный email при поиске кошелька: {}", email);
            throw new IllegalArgumentWalletException("Некорректный формат данных");
        }

        return walletRepository.findByEmail(email)
                .map(w -> {
                    // Используем данные из кошелька напрямую для безопасности
                    return new WalletRegisteredRequest(w.getEmail(), w.getBalance());
                })
                .orElseThrow(() -> {
                    log.warn("Запрос данных несуществующего кошелька для email: {}", email);
                    return new WalletRegisteredNotFoundException("Кошелек не найден для: " + email);
                });
    }

    @Override
    public WalletDto getWalletById(String walletId) {
        log.info("Запрос кошелька по ID: {}", walletId);
        UUID uuid = parseUuid(walletId);

        // Используем метод findById, но предотвращаем рекурсию через маппинг
        return walletRepository.findById(uuid)
                .map(wallet -> {
                    log.debug("Кошелек найден, преобразуем в DTO");
                    return walletMapper.walletToWalletDto(wallet);
                })
                .orElseThrow(() -> {
                    log.warn("Кошелек не найден: {}", walletId);
                    return new WalletRegisteredNotFoundException("Кошелек с ID " + walletId + " не найден");
                });
    }

    @Override
    public BigDecimal operationGetBalance(String id) {
        log.info("Запрос баланса для ID: {}", id);
        UUID uuid = parseUuid(id);

        // Здесь нам не нужен весь объект WalletDto, берем только баланс напрямую из сущности
        // Это быстрее и исключает любые ошибки маппинга
        return walletRepository.findById(uuid)
                .map(WalletRegistered::getBalance)
                .orElseThrow(() -> {
                    log.warn("Не удалось получить баланс, кошелек не найден: {}", id);
                    return new WalletRegisteredNotFoundException("Кошелек не найден");
                });
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

    @Override
    public WalletRegisteredRequest findByUsername(String username) {
        log.info("Поиск кошелька для пользователя (username): {}", username);

        // 1. Находим пользователя по username
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Пользователь не найден при поиске кошелька: {}", username);
                    return new WalletRegisteredNotFoundException("Пользователь не найден: " + username);
                });

        // 2. Находим кошелек, связанный с этим пользователем
        // Предполагаем, что в репозитории есть метод findByUser или ищем по email, который есть в User
        return walletRepository.findByEmail(user.getEmail())
                .map(w -> new WalletRegisteredRequest(w.getEmail(), w.getBalance()))
                .orElseThrow(() -> {
                    log.warn("Кошелек не найден для пользователя: {}", username);
                    return new WalletRegisteredNotFoundException("Кошелек не найден для пользователя: " + username);
                });
    }
}

