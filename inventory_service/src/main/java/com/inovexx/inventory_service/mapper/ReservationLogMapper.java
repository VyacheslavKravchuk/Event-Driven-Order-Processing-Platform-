package com.inovexx.inventory_service.mapper;


import com.inovexx.inventory_service.dto.InventoryDto;
import com.inovexx.inventory_service.dto.ReservationLogDto;
import com.inovexx.inventory_service.entity.Inventory;
import com.inovexx.inventory_service.entity.ReservationLog;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReservationLogMapper {

    InventoryDto inventoryLogToInventoryLogDto(ReservationLog reservationLog);

    Inventory inventoryLogDtoToInventoryLog(ReservationLogDto reservationLogDto);

    void updateInventoryLogFromDto(Inventory productDto, @MappingTarget Inventory inventory);
}
