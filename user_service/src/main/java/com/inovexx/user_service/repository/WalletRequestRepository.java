package com.inovexx.user_service.repository;

import com.inovexx.user_service.entity.wallet.WalletTransaction;
import com.inovexx.user_service.enums.Operation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRequestRepository extends JpaRepository<WalletTransaction, UUID> {

    /**
     * Поиск по ID с пессимистичной блокировкой.
     * Используется для предотвращения одновременного изменения одного кошелька.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletTransaction> findById(UUID uuid);

    /**
     * КЛЮЧЕВОЙ МЕТОД ДЛЯ САГИ И ИДЕМПОТЕНТНОСТИ.
     * Проверяет, была ли уже транзакция по конкретному заказу и типу (DEDUCT/DEPOSIT).
     * Это защищает от двойного списания, если сеть "моргнула".
     */
    Optional<WalletTransaction> findByOrderIdAndOperationType(Long orderId, Operation operationType);

    /**
     * Проверка существования транзакции (более легковесный метод для boolean проверок).
     */
    boolean existsByOrderIdAndOperationType(Long orderId, Operation operationType);
}
