package com.inovexx.user_service.entity.wallet;

import com.inovexx.user_service.enums.Operation;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor // Полезно для тестов
@Builder // Упрощает создание объектов в коде
@Table(name = "wallet_transactions")
public class WalletTransaction { // Переименовано для ясности (было WalletRequest)

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_id")
    private UUID id; // Упростим название до id

    // Сделаем nullable = true, так как при DEPOSIT/WITHDRAW заказа может не быть
    @Column(name = "order_id", nullable = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private Operation operationType;

    @Column(nullable = false, precision = 19, scale = 4) // Рекомендуется точность для денег
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private WalletRegistered wallet;

}