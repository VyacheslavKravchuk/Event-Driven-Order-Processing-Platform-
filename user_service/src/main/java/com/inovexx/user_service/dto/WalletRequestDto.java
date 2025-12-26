package com.inovexx.user_service.dto;

import com.inovexx.user_service.enums.Operation;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WalletRequestDto (

        UUID transactionId,

        Operation operationType,

        BigDecimal amount,

        // Автоматическая дата создания
        LocalDateTime createdAt,

        UUID walletId
){}
