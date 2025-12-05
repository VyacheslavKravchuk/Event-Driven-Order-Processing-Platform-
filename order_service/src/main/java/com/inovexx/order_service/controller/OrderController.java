package com.inovexx.order_service.controller;

import com.inovexx.order_service.dto.OrderDto;
import com.inovexx.order_service.enums.OrderStatus;
import com.inovexx.order_service.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Order Management", description = "API для управления заказами")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class OrderController {


    private final OrderService orderService;
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);



    @Operation(summary = "Получить список всех заказов")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список успешно получен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDto.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав (ROLE_MANAGER/ADMIN)")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<List<OrderDto>> getAllOrder() {
        logger.info("Запрос на получение всех записей заказов");
        List<OrderDto> orderDtos = orderService.findAll();
        logger.info("Получено {} записей инвентаря", orderDtos.size());
        return ResponseEntity.ok(orderDtos);
    }

    @Operation(summary = "Получить запись инвентаря по ID продукта")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Запись найдена"),
            @ApiResponse(responseCode = "404", description = "Запись не найдена"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав (ROLE_MANAGER/ADMIN)")
    })
    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<OrderDto> getOrderById(
            @Parameter(description = "ID продукта для поиска запаса") @PathVariable Long orderId) {
        logger.info("Запрос на получение записи заказа по ID: {}", orderId);
        return orderService.findById(orderId)
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
    public ResponseEntity<OrderDto> createOrder(@RequestBody OrderDto orderDto) {
        logger.info("Запрос на создание нового заказа: {}", orderDto);
        OrderDto createdOrder = orderService.createOrder(orderDto);
        logger.info("Заказ создан для клиента с ID: {}", createdOrder.customerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdOrder);
    }

    @Operation(summary = "Обновить статус заказа")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Статус успешно обновлен"),
            @ApiResponse(responseCode = "404", description = "Продукт не найден"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав (ROLE_MANAGER/ADMIN)")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDto> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus newStatus) {

        OrderDto updatedDto =  orderService.updateOrderStatus(id, newStatus);
        return ResponseEntity.ok(updatedDto);
    }

    @Operation(summary = "Удалить запись заказа (только Администратор)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Заказ успешно удален"),
            @ApiResponse(responseCode = "404", description = "Запись не найдена"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав (ROLE_ADMIN)")
    })
    @DeleteMapping("/{orderId/cancel}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteOrder(
            @Parameter(description = "ID продукта") @RequestParam Long orderId) {
        logger.info("Запрос на удаление записи заказа с ID: {}", orderId);

        orderService.deleteOrderById(orderId);
        logger.info("Запись заказа с ID: {} успешно удалена", orderId);
        return ResponseEntity.noContent().build();

    }
}
