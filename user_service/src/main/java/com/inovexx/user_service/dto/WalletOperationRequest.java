package com.inovexx.user_service.dto;

import com.inovexx.user_service.enums.Operation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WalletOperationRequest(
        @NotNull(message = "Тип операции обязателен")
        Operation operationType,

        @Positive(message = "Сумма должна быть больше нуля")
        @NotNull(message = "Сумма обязательна")
        BigDecimal amount
) {}
