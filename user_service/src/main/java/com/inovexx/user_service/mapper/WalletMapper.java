package com.inovexx.user_service.mapper;

import com.inovexx.user_service.dto.WalletDto;
import com.inovexx.user_service.entity.wallet.WalletRegistered;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WalletMapper {

    WalletDto walletToWalletDto(WalletRegistered walletRegistered);

    WalletRegistered walletDtoToWallet(WalletDto walletDto);

    void updateWalletFromDto(WalletDto walletDto, @MappingTarget WalletRegistered wallet);
}
