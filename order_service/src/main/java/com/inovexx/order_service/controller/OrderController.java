package com.inovexx.order_service.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Order Management", description = "API для управления заказами")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Получить список всех заказов")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список успешно получен"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<List<OrderDto>> getAllOrders() {
        log.info("Запрос на получение всех заказов");
        return ResponseEntity.ok(orderService.findAll());
    }

    @Operation(summary = "Получить заказ по ID")
    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<OrderDto> getOrderById(
            @Parameter(description = "ID заказа") @PathVariable Long orderId) {
        log.info("Запрос на получение заказа по ID: {}", orderId);
        return orderService.findById(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(ResponseEntity.notFound()::build);
    }

    @Operation(summary = "Создать новый заказ")
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_ADMIN')") // Обычно USER тоже может создавать заказы
    public ResponseEntity<OrderDto> createOrder(@RequestBody OrderDto orderDto) throws JsonProcessingException {
        log.info("Запрос на создание нового заказа для пользователя: {}", orderDto.userId());
        OrderDto createdOrder = orderService.createOrder(orderDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdOrder);
    }

    @SneakyThrows
    @Operation(summary = "Обновить статус заказа")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    public ResponseEntity<OrderDto> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus newStatus) { // Убрал лишний throws, если сервис его не кидает
        log.info("Обновление статуса заказа {} на {}", id, newStatus);
        return ResponseEntity.ok(orderService.updateOrderStatus(id, newStatus));
    }

    @SneakyThrows
    @Operation(summary = "Удалить/Отменить заказ")
    @DeleteMapping("/{orderId}") // Исправлена опечатка в пути {orderId/cancel} -> {orderId}
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteOrder(
            @PathVariable Long orderId) { // Исправлено: @PathVariable вместо @RequestParam для соответствия пути
        log.info("Запрос на удаление заказа с ID: {}", orderId);
        orderService.deleteOrderById(orderId);
        return ResponseEntity.noContent().build();
    }
}

