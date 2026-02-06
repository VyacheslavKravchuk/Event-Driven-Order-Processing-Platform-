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

    // Для обычного поиска (регистрация, логин)
    Optional<WalletRegistered> findByEmail(String email);

    // Специальный метод для сервиса транзакций (ввод/вывод денег)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletRegistered w WHERE w.walletId = :id")
    Optional<WalletRegistered> findByIdForUpdate(@Param("id") UUID id);

    // Если нужно заблокировать по email (например, при пополнении по email)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletRegistered w WHERE w.email = :email")
    Optional<WalletRegistered> findByEmailForUpdate(@Param("email") String email);
}
