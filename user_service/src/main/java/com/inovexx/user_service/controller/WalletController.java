package com.inovexx.user_service.controller;

import com.inovexx.user_service.dto.WalletDto;
import com.inovexx.user_service.dto.WalletRegisteredRequest;
import com.inovexx.user_service.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Интернет-кошелек", description = "Управление кошельками пользователей")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/register")
    @Operation(summary = "Регистрация нового кошелька")
    public ResponseEntity<WalletDto> registerWallet(@RequestBody WalletDto walletDto) {
        log.info("Получен запрос на регистрацию кошелька для email: {}", walletDto);

        walletService.createWallet(walletDto);

        log.info("Кошелек успешно создан для пользователя с ID: {}", walletDto.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(walletDto);
    }

    @PostMapping("/login")
    @Operation(summary = "Вход в систему кошелька")
    public ResponseEntity<WalletRegisteredRequest> loginWallet(@RequestParam String email) {
        log.info("Попытка входа в кошелек для email: {}", email);

        WalletRegisteredRequest response = walletService.findByEmailWallet(email);

        log.info("Пользователь {} успешно вошел в систему кошелька", email);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{wallet_id}")
    @Operation(summary = "Получить информацию о кошельке")
    public ResponseEntity<WalletDto> getWalletById(@PathVariable("wallet_id") String walletId) {
        log.info("Запрос данных кошелька с ID: {}", walletId);

        WalletDto wallet = walletService.getWalletById(walletId);

        log.debug("Данные кошелька {} успешно извлечены", walletId);
        return ResponseEntity.ok(wallet);
    }

    @GetMapping("/{wallet_id}/balance")
    @Operation(summary = "Запрос текущего баланса")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable("wallet_id") String walletId) {
        log.info("Запрос баланса для кошелька: {}", walletId);

       BigDecimal balance = walletService.getWalletById(walletId).balance();

        log.info("Баланс кошелька {} составляет: {}", walletId, balance);
        return ResponseEntity.ok(balance);
    }
}



