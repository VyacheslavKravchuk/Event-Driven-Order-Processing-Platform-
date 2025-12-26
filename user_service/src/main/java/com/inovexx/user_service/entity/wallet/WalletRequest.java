package com.inovexx.user_service.entity.wallet;

import com.inovexx.user_service.enums.Operation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Класс для сущности транзакций одного пользователя по своему кошельку
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "wallet_transactions")
public class WalletRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    private Operation operationType;

    private BigDecimal amount;

    @CreationTimestamp // Автоматическая дата создания
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private WalletRegistered wallet;

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
                "transactionId = " + transactionId + ", " +
                "operationType = " + operationType + ", " +
                "amount = " + amount + ", " +
                "createdAt = " + createdAt + ")";
    }
}
