package com.inovexx.inventory_service.controller;

import com.inovexx.inventory_service.dto.InventoryDto;
import com.inovexx.inventory_service.entity.Inventory;
import com.inovexx.inventory_service.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NoSuchElementException;

@Tag(name = "Inventory Management", description = "API для управления запасами на складе")
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;
    private static final Logger logger = LoggerFactory.getLogger(InventoryController.class);

    @Operation(summary = "Получить список всех записей инвентаря")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список успешно получен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = InventoryDto.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав (ROLE_MANAGER/ADMIN)")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<List<InventoryDto>> getAllInventory() {
        logger.info("Запрос на получение всех записей инвентаря");
        List<InventoryDto> inventoryDtos = inventoryService.findAll();
        logger.info("Получено {} записей инвентаря", inventoryDtos.size());
        return ResponseEntity.ok(inventoryDtos);
    }

    @Operation(summary = "Получить запись инвентаря по ID продукта")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запись найдена"),
            @ApiResponse(responseCode = "404", description = "Запись не найдена"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав (ROLE_MANAGER/ADMIN)")
    })
    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<InventoryDto> getInventoryById(
            @Parameter(description = "ID продукта для поиска запаса") @PathVariable Long productId) {
        logger.info("Запрос на получение записи инвентаря по ID продукта: {}", productId);
        return inventoryService.findById(productId)
                .map(ResponseEntity::ok)
                .orElseGet(ResponseEntity.notFound()::build);
    }

    @Operation(summary = "Создать новую запись инвентаря (только Администратор)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Запись успешно создана"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав (ROLE_ADMIN)")
    })
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<InventoryDto> createInventory(@RequestBody InventoryDto inventoryDto) {
        logger.info("Запрос на создание новой записи инвентаря: {}", inventoryDto);
        InventoryDto savedDto = inventoryService.create(inventoryDto);
        logger.info("Запись инвентаря успешно создана с ID: {}", savedDto.getProductId());
        return ResponseEntity.status(HttpStatus.CREATED).body(savedDto);
    }

    @Operation(summary = "Обновить количество запасов для продукта")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запас успешно обновлен"),
            @ApiResponse(responseCode = "404", description = "Продукт не найден"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав (ROLE_MANAGER/ADMIN)")
    })
    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<InventoryDto> updateInventoryStock(
            @Parameter(description = "ID продукта") @PathVariable Long productId,
            @Parameter(description = "Новое количество запасов") @RequestParam int newStock) {
        logger.info("Запрос на обновление запасов для продукта ID: {}, новое количество: {}", productId, newStock);
        try {
            InventoryDto updatedDto = inventoryService.updateStock(productId, newStock);
            logger.info("Запасы для продукта ID: {} успешно обновлены", productId);
            return ResponseEntity.ok(updatedDto);
        } catch (NoSuchElementException e) {
            logger.warn("Продукт с ID: {} не найден при попытке обновления запасов", productId);
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Удалить запись инвентаря (только Администратор)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Запись успешно удалена"),
            @ApiResponse(responseCode = "404", description = "Запись не найдена"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав (ROLE_ADMIN)")
    })
    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteInventory(
            @Parameter(description = "ID продукта") @PathVariable Long productId) {
        logger.info("Запрос на удаление записи инвентаря с ID продукта: {}", productId);
        try {
            inventoryService.deleteById(productId);
            logger.info("Запись инвентаря с ID продукта: {} успешно удалена", productId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Ошибка при удалении записи инвентаря с ID продукта: {}", productId, e);
            return ResponseEntity.notFound().build();
        }
    }
}
