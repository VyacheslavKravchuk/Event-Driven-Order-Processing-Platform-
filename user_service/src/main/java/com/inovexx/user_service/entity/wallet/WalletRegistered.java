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
@NoArgsConstructor
@Table(name = "wallets")
public class WalletRegistered {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "wallet_id")
    private UUID walletId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(unique = true, nullable = false)
    private String email;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id", unique = true)
    @ToString.Exclude // Важно: предотвращает StackOverflowError
    private User user;

    @Override
    public String toString() {
        return "WalletRegistered{" +
                "walletId=" + walletId +
                ", balance=" + balance +
                ", email='" + email + '\'' +
                '}';
    }
}
