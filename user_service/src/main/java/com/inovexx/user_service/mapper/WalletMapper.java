package com.inovexx.user_service.mapper;

import com.inovexx.user_service.dto.WalletDto;
import com.inovexx.user_service.entity.wallet.WalletRegistered;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WalletMapper {

    @Mapping(target = "userId", source = "user.id")
    WalletDto walletToWalletDto(WalletRegistered walletRegistered);

    @Mapping(target = "user", ignore = true)
    @Mapping(target = "walletId", ignore = true)
    WalletRegistered walletDtoToWallet(WalletDto walletDto);

    @Mapping(target = "user", ignore = true)     // Не обновляем связь с User через DTO
    @Mapping(target = "walletId", ignore = true) // Не меняем ID существующего кошелька
    @Mapping(target = "balance", ignore = true)  // Баланс обновляем только через сервис транзакций
    void updateWalletFromDto(WalletDto walletDto, @MappingTarget WalletRegistered wallet);
}