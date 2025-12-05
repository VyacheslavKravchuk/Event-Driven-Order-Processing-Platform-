package com.inovexx.order_service.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<ErrorDetails> createErrorResponse(Exception ex, WebRequest request, HttpStatus status, String message) {
        logger.warn("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                message,
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorDetails, status);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorDetails> handleProductNotFoundException(ProductNotFoundException ex, WebRequest request) {
        logger.warn("Продукт не найден: {}", ex.getMessage());
        return createErrorResponse(ex, request, HttpStatus.NOT_FOUND, "Продукт не найден");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorDetails> handleIllegalStateException(IllegalStateException ex, WebRequest request) {
        logger.warn("Некорректное состояние: {}", ex.getMessage());
        return createErrorResponse(ex, request, HttpStatus.BAD_REQUEST, "Некорректное состояние");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDetails> handleGeneralException(Exception ex, WebRequest request) {
        logger.error("Необработанное исключение: ", ex);
        return createErrorResponse(ex, request, HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .reduce("", (acc, error) -> acc + error + "; ");

        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Ошибка валидации: " + errorMessage,
                request.getDescription(false)
        );
        logger.warn("Ошибка валидации: {}", errorMessage);

        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDetails> handleGlobalException(Exception ex, WebRequest request) {
        logger.error("Поймано глобальное исключение: {}", ex.getMessage(), ex);
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Внутренняя ошибка сервера: произошла непредвиденная ошибка",
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({SignatureException.class, MalformedJwtException.class, UnsupportedJwtException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorDetails> handleInvalidJwtExceptions(Exception ex, WebRequest request) {
        return createErrorResponse(ex, request, HttpStatus.UNAUTHORIZED, "Недействительный JWT токен");
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ErrorDetails> handleExpiredJwtException(ExpiredJwtException ex,
                                                                  WebRequest request) {
        return createErrorResponse(ex, request, HttpStatus.UNAUTHORIZED, "Срок действия JWT токена истек");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDetails> handleAccessDeniedException(AccessDeniedException ex,
                                                                    WebRequest request) {
        return createErrorResponse(ex, request, HttpStatus.FORBIDDEN, "Доступ запрещен: у вас нет необходимой роли/разрешения");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDetails> handleIllegalArgumentException(IllegalArgumentException ex,
                                                                       WebRequest request) {
        logger.error("Некорректный аргумент: {}", ex.getMessage());
        ErrorDetails errorResponse = new ErrorDetails(
                LocalDateTime.now(),
                "Некорректный аргумент",
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidStockLevelException.class)
    public ResponseEntity<ErrorDetails> handleInvalidStockLevelException(InvalidStockLevelException ex,
                                                                         WebRequest request) {
        logger.error("Недопустимое количество продуктов: {}", ex.getMessage());
        ErrorDetails errorResponse = new ErrorDetails(
                LocalDateTime.now(),
                "Недопустимое количество продуктов",
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
}



