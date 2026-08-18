package com.inovexx.order_service.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private ResponseEntity<ErrorDetails> createErrorResponse(Exception ex, WebRequest request, HttpStatus status, String message) {
        log.warn("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                message,
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorDetails, status);
    }

    // --- Бизнес-исключения ---

    // Добавьте в GlobalExceptionHandler
    @ExceptionHandler({OrderNotFoundException.class, ProductNotFoundException.class})
    public ResponseEntity<ErrorDetails> handleNotFoundExceptions(RuntimeException ex, WebRequest request) {
        return createErrorResponse(ex, request, HttpStatus.NOT_FOUND, ex.getMessage());
    }


    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class, InvalidStockLevelException.class})
    public ResponseEntity<ErrorDetails> handleBadRequestExceptions(Exception ex, WebRequest request) {
        return createErrorResponse(ex, request, HttpStatus.BAD_REQUEST, "Ошибка запроса: " + ex.getMessage());
    }

    // --- Безопасность (JWT и Доступ) ---

    @ExceptionHandler({SignatureException.class, MalformedJwtException.class, UnsupportedJwtException.class, ExpiredJwtException.class})
    public ResponseEntity<ErrorDetails> handleJwtExceptions(Exception ex, WebRequest request) {
        String msg = ex instanceof ExpiredJwtException ? "Срок действия токена истек" : "Недействительный JWT токен";
        return createErrorResponse(ex, request, HttpStatus.UNAUTHORIZED, msg);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDetails> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        return createErrorResponse(ex, request, HttpStatus.FORBIDDEN, "Доступ запрещен: недостаточно прав");
    }

    // --- Валидация @Valid ---

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ErrorDetails errorDetails = new ErrorDetails(LocalDateTime.now(), "Ошибка валидации: " + errorMessage, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    // --- Глобальный перехватчик (ОДИН МЕТОД) ---

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDetails> handleAllUncaughtExceptions(Exception ex, WebRequest request) {
        log.error("Критическая ошибка сервера: ", ex);
        return createErrorResponse(ex, request, HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера");
    }
}




