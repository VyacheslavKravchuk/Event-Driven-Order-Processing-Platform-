package com.inovexx.user_service.repository;

import com.inovexx.user_service.entity.wallet.WalletRegistered;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<WalletRegistered, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletRegistered> findByUserId(Long userId);

    // Для обычного поиска (регистрация, логин)
    Optional<WalletRegistered> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE) // Блокирует строку до конца транзакции
    @Query("SELECT w FROM WalletRegistered w WHERE w.walletId = :id")
    Optional<WalletRegistered> findByIdWithLock(UUID id);

    // Специальный метод для сервиса транзакций (ввод/вывод денег)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletRegistered w WHERE w.walletId = :id")
    Optional<WalletRegistered> findByIdForUpdate(@Param("id") UUID id);

}
