package com.inovexx.product_service.controller;

import com.inovexx.product_service.dto.ProductRequest;
import com.inovexx.product_service.dto.ProductResponse;
import com.inovexx.product_service.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Product API", description = "API для управления продуктами")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать новый продукт")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Продукт успешно создан"),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос", content = @Content),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера", content = @Content)
    })
    public void createProduct(@RequestBody ProductRequest productRequest) {
        log.info("Получен запрос на создание продукта: {}", productRequest);
        productService.createProduct(productRequest);
        log.info("Продукт создан."); // Лог на русском
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    @Operation(summary = "Получить все продукты")
    @ApiResponse(responseCode = "200", description = "Список продуктов",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProductResponse.class)))
    public List<ProductResponse> getAllProducts() {
        log.info("Получен запрос на получение всех продуктов.");
        List<ProductResponse> products = productService.getAllProducts();
        log.info("Возвращено {} продуктов.", products.size());
        return products;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    @Operation(summary = "Обновить существующий продукт по ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Продукт успешно обновлен",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProductResponse.class))),
            @ApiResponse(responseCode = "404", description = "Продукт не найден", content = @Content),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос", content = @Content),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера", content = @Content)
    })
    public ProductResponse updateProduct(@PathVariable String id, @RequestBody ProductRequest productRequest) {
        log.info("Получен запрос на обновление продукта с ID: {}, данные: {}", id, productRequest); // Лог на русском
        ProductResponse productResponse = productService.updateProduct(id, productRequest);
        log.info("Продукт с ID: {} обновлен.", id); // Лог на русском
        return productResponse;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_MANAGER', 'ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить продукт по ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Продукт успешно удален"),
            @ApiResponse(responseCode = "404", description = "Продукт не найден", content = @Content),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера", content = @Content)
    })
    public void deleteProduct(@PathVariable String id) {
        log.info("Получен запрос на удаление продукта с ID: {}", id);
        productService.deleteProduct(id);
        log.info("Продукт с ID: {} удален.", id);
    }
}