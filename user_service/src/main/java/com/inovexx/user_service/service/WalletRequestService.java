package com.inovexx.user_service.service;

import com.inovexx.user_service.dto.WalletOperationRequest;
import com.inovexx.user_service.dto.WalletRequestDto;
import com.inovexx.user_service.enums.Operation;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletRequestService {

    WalletOperationRequest operationInputAndOutput(String walletId,
                                                   WalletOperationRequest walletOperationRequest);

    WalletRequestDto processOrderPayment(Long orderId, UUID walletId, BigDecimal amount, Operation operation);

}
