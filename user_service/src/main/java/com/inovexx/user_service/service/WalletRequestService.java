package com.inovexx.user_service.service;

import com.inovexx.user_service.dto.WalletRequestDto;

public interface WalletRequestService {

    WalletRequestDto operationInputAndOutput(String walletId,
                                             WalletRequestDto walletRequestDto);

}
