package com.inovexx.user_service.service;

import com.inovexx.user_service.dto.WalletRequestDto;
import com.inovexx.user_service.entity.wallet.WalletRequest;

public interface WalletRequestService {

    WalletRequestDto operationInputAndOutput(String walletId,
                                             WalletRequestDto walletRequestDto);

}
