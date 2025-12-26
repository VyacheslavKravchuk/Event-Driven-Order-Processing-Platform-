package com.inovexx.user_service.service;

import com.inovexx.user_service.dto.WalletDto;
import com.inovexx.user_service.dto.WalletRegisteredRequest;
import com.inovexx.user_service.entity.wallet.WalletRegistered;

import java.math.BigDecimal;

public interface WalletService {

    WalletDto createWallet(WalletDto walletDto);

    WalletDto getWalletById(String walletId);

    BigDecimal operationGetBalance(String id);

    boolean isValidEmail(String email);

    WalletRegisteredRequest findByEmailWallet(String email);
}
