package com.inovexx.user_service.mapper;

import com.inovexx.user_service.dto.WalletRequestDto;
import com.inovexx.user_service.entity.wallet.WalletRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WalletRequestMapper {

    // Мапим сущность транзакции в DTO
    WalletRequestDto toDto(WalletRequest walletRequest);

    // Мапим DTO в сущность транзакции
    @Mapping(target = "transactionId", ignore = true)
    @Mapping(target = "wallet", ignore = true) // Кошелек установим вручную в сервисе
    WalletRequest toEntity(WalletRequestDto dto);
}
