package com.inovexx.user_service.entity.wallet;

import com.inovexx.user_service.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Класс для сущности зарегестрированных кошельков
 */

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@Table(name = "wallets")
public class WalletRegistered {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Для UUID в Spring Boot 3 лучше так
    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(nullable = false)
    private BigDecimal balance;

    // Связь 1-к-1: у одного юзера один кошелек
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", unique = true)
    private User user;

}
