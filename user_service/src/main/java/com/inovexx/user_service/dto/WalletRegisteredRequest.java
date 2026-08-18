package com.inovexx.user_service.dto;

import java.math.BigDecimal;

public record WalletRegisteredRequest (

        String email,

        BigDecimal balance

) {}
