package com.inovexx.user_service.controller;

import com.inovexx.user_service.dto.WalletRequestDto;
import com.inovexx.user_service.service.WalletRequestService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/transactions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Операции с кошельком", description = "Управление вводом и выводом средств")
public class WalletRequestController {

    private final WalletRequestService walletRequestService;

    @PostMapping("/{walletId}")
    @Operation(
            summary = "Проведение транзакции",
            description = "Позволяет выполнить операцию пополнения (DEPOSIT) или снятия (WITHDRAW) средств"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Транзакция успешно проведена"),
            @ApiResponse(responseCode = "400", description = "Недостаточно средств или неверные параметры"),
            @ApiResponse(responseCode = "404", description = "Кошелек не найден")
    })
    public ResponseEntity<WalletRequestDto> executeTransaction(
            @Parameter(description = "UUID кошелька", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("walletId") String id,
            @RequestBody WalletRequestDto walletRequestDto) {

        log.info("Запрос на транзакцию для кошелька {}: тип={}, сумма={}",
                id, walletRequestDto.operationType(), walletRequestDto.amount());

        WalletRequestDto result = walletRequestService.operationInputAndOutput(id, walletRequestDto);

        log.info("Транзакция успешно завершена. ID транзакции: {}", result.transactionId());
        return ResponseEntity.ok(result);
    }
}

