package com.inovexx.product_service.exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFoundException(ProductNotFoundException ex,
                                                                  WebRequest request) {
        log.warn("Обработка исключения ResourceNotFoundException: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", ex.getMessage());
        body.put("status", HttpStatus.NOT_FOUND.value());  // HTTP статус 404

        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ProductAlreadyExistsException.class)
    public ResponseEntity<Object> handleProductAlreadyExistsException(ProductAlreadyExistsException ex,
                                                                      WebRequest request) {
        log.warn("Обработка исключения ProductAlreadyExistsException: {}", ex.getMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", ex.getMessage());
        body.put("status", HttpStatus.CONFLICT.value()); // HTTP статус 409

        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(Exception ex, WebRequest request) {
        log.error("Непредвиденная ошибка: {}", ex.getMessage(), ex);
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", "Произошла внутренняя ошибка сервера."); // Сообщение пользователю
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value()); // HTTP статус 500
        // ex.printStackTrace(); // Вывод stack trace в консоль (для отладки)

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }



}