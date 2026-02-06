package com.inovexx.user_service.controller;

import com.inovexx.user_service.dto.WalletDto;
import com.inovexx.user_service.dto.WalletRegisteredRequest;
import com.inovexx.user_service.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/wallets")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Интернет-кошелек", description = "API для управления кошельками пользователей, проверки баланса и регистрации")
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/register")
    @Operation(
            summary = "Регистрация нового кошелька",
            description = "Создает новый электронный кошелек в системе на основе переданных данных пользователя"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Кошелек успешно создан",
                    content = @Content(schema = @Schema(implementation = WalletDto.class))),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации входных данных"),
            @ApiResponse(responseCode = "409", description = "Кошелек с таким email уже существует")
    })
    public ResponseEntity<WalletDto> registerWallet(@Valid @RequestBody WalletDto walletDto) {
        log.info("Запрос на регистрацию кошелька: {}", walletDto.email());
        WalletDto result = walletService.createWallet(walletDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/me")
    @Operation(
            summary = "Получить данные своего кошелька",
            description = "Извлекает идентификатор пользователя (email) из JWT-токена и возвращает расширенную информацию о кошельке"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Данные успешно получены"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "404", description = "Кошелек для текущего пользователя не найден")
    })
    public ResponseEntity<WalletRegisteredRequest> getWalletMe(@Parameter(hidden = true) Principal principal) {
        // Теперь мы берем username (в вашем случае "usex") и идем с ним в сервис
        String username = principal.getName();
        log.info("Запрос кошелька для пользователя: {}", username);

        WalletRegisteredRequest response = walletService.findByUsername(username);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{wallet_id}")
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    @Operation(
            summary = "Получить информацию о кошельке по ID",
            description = "Доступно только пользователям с ролями MANAGER или ADMIN"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Кошелек найден"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав для выполнения операции"),
            @ApiResponse(responseCode = "404", description = "Кошелек с указанным ID не существует")
    })
    public ResponseEntity<WalletDto> getWalletById(
            @PathVariable("wallet_id") String walletId) {
        log.info("Запрос данных кошелька с ID: {}", walletId);
        WalletDto wallet = walletService.getWalletById(walletId);
        return ResponseEntity.ok(wallet);
    }

    @GetMapping("/{wallet_id}/balance")
    @Operation(
            summary = "Запросить текущий баланс",
            description = "Возвращает текущий остаток денежных средств на кошельке"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Баланс успешно получен",
                    content = @Content(schema = @Schema(implementation = BigDecimal.class))),
            @ApiResponse(responseCode = "404", description = "Кошелек не найден")
    })
    public ResponseEntity<BigDecimal> getBalance(
            @Parameter(description = "ID кошелька для проверки баланса", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable("wallet_id") String walletId) {
        log.info("Запрос баланса: {}", walletId);
        BigDecimal balance = walletService.operationGetBalance(walletId);
        return ResponseEntity.ok(balance);
    }
}