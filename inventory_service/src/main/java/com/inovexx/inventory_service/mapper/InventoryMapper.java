package com.inovexx.inventory_service.mapper;



import com.inovexx.inventory_service.dto.InventoryDto;
import com.inovexx.inventory_service.entity.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InventoryMapper {

    InventoryDto inventoryToInventoryDto(Inventory inventory);

    Inventory inventoryDtoToInventory(InventoryDto inventoryDto);

    void updateInventoryFromDto(Inventory productDto, @MappingTarget Inventory inventory);
}
