package com.inovexx.user_service.mapper;

import com.inovexx.user_service.dto.WalletOperationRequest;
import com.inovexx.user_service.dto.WalletRequestDto;
import com.inovexx.user_service.entity.wallet.WalletTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WalletRequestMapper {

    // --- 1. Из Сущности в DTO (Output) ---

    // Полный DTO (например, для истории транзакций)
    WalletRequestDto toFullDto(WalletTransaction entity);

    // Сокращенный DTO (если нужно вернуть только подтверждение операции)
    WalletOperationRequest toOperationDto(WalletTransaction entity);

    // --- 2. Из DTO в Сущность (Input) ---

    // Маппинг из расширенного DTO (где может быть orderId)
    // Учитываем, что в сущности поле называется 'id' или 'transactionId'
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "wallet", ignore = true) // Установим объект Wallet в сервисе
    WalletTransaction fromFullDtoToEntity(WalletRequestDto dto);

    // Маппинг из упрощенного DTO (для пополнения/снятия)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderId", ignore = true) // В OperationRequest этого поля нет
    @Mapping(target = "wallet", ignore = true)
    WalletTransaction fromOperationDtoToEntity(WalletOperationRequest dto);

    // --- 3. Обновление (Helper) ---

    void updateEntityFromDto(WalletOperationRequest dto, @MappingTarget WalletTransaction entity);
}
