package com.inovexx.auth_service.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Глобальный обработчик исключений для REST API.  Перехватывает и обрабатывает исключения,
 * возникающие в контроллерах, и преобразует их в JSON ответы с соответствующим HTTP статусом.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Создает JSON ответ об ошибке для заданного HTTP статуса, сообщения и исключения.
     *
     * @param status  HTTP статус ошибки.
     * @param message Сообщение об ошибке для пользователя.
     * @param ex      Исключение, вызвавшее ошибку (для логирования).
     * @param errorId Уникальный идентификатор ошибки (опционально).
     * @return ResponseEntity с JSON телом ответа об ошибке.
     */
    private ResponseEntity<Object> createErrorResponse(HttpStatus status, String message,
                                                       Exception ex, String errorId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", new Date());
        body.put("status", status.value());
        body.put("error", message);
        if (errorId != null) {
            body.put("errorId", errorId);
        }
        if (ex != null) {
            body.put("exception", ex.getClass().getName());
        }
        logger.error(message, ex);
        return new ResponseEntity<>(body, status);
    }

    /**
     * Обрабатывает исключение {@link IllegalArgumentException}.
     * Возвращает HTTP статус 400 (BAD REQUEST) с сообщением об ошибке.
     *
     * @param e Исключение {@link IllegalArgumentException}.
     * @return ResponseEntity с JSON телом ответа об ошибке.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException e) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, "Неверные данные: " + e.getMessage(), e, null);
    }

    /**
     * Обрабатывает исключение {@link AuthenticationException}.
     * Возвращает HTTP статус 401 (UNAUTHORIZED) с сообщением об ошибке.
     *
     * @param e Исключение {@link AuthenticationException}.
     * @return ResponseEntity с JSON телом ответа об ошибке.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException e) {
        return createErrorResponse(HttpStatus.UNAUTHORIZED, "Неверные учетные данные", e, null);
    }

    /**
     * Обрабатывает исключение {@link NoSuchElementException}.
     * Возвращает HTTP статус 404 (NOT FOUND) с сообщением об ошибке.
     *
     * @param ex Исключение {@link NoSuchElementException}.
     * @return ResponseEntity с JSON телом ответа об ошибке.
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Object> handleNoSuchElementException(NoSuchElementException ex) {
        return createErrorResponse(HttpStatus.NOT_FOUND,
                "Ресурс не найден: " + ex.getMessage(), ex, null);
    }

    /**
     * Обрабатывает исключение {@link MethodArgumentNotValidException}, возникающее при валидации данных.
     * Возвращает HTTP статус 400 (BAD REQUEST) со списком ошибок валидации.
     *
     * @param ex Исключение {@link MethodArgumentNotValidException}.
     * @return ResponseEntity с JSON телом ответа об ошибке, содержащим список ошибок валидации.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationException(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> {
                    Map<String, String> error = new HashMap<>();
                    error.put("field", fieldError.getField());
                    error.put("message", fieldError.getDefaultMessage());
                    return error;
                })
                .collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", new Date());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Ошибка валидации данных"); // Общее сообщение
        body.put("errors", errors);
        logger.error("Ошибка валидации: {}", body);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает все необработанные исключения типа {@link Exception}.
     * Возвращает HTTP статус 500 (INTERNAL SERVER ERROR) с общим сообщением об ошибке.
     * Генерирует уникальный ID ошибки для отслеживания в логах.
     *
     * @param ex Исключение {@link Exception}.
     * @return ResponseEntity с JSON телом ответа об ошибке.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception ex) {
        String errorId = UUID.randomUUID().toString();
        String message = "Внутренняя ошибка сервера. Пожалуйста, попробуйте позже.";
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message, ex, errorId);
    }

    /**
     * Обрабатывает исключение {@link UsernameNotFoundException}.
     * Возвращает HTTP статус 404 (NOT FOUND) с сообщением об ошибке.
     *
     * @param e Исключение {@link UsernameNotFoundException}.
     * @return ResponseEntity с JSON телом ответа об ошибке.
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Object> handleUsernameNotFoundException(UsernameNotFoundException e) {
        return createErrorResponse(HttpStatus.NOT_FOUND, "Пользователь не найден", e, null);
    }

    /**
     * Обрабатывает исключение {@link OptimisticLockingFailureException}.
     * Возвращает HTTP статус 409 (CONFLICT) с сообщением об ошибке.
     *
     * @param ex Исключение {@link OptimisticLockingFailureException}.
     * @return ResponseEntity с JSON телом ответа об ошибке.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLockingFailureException(OptimisticLockingFailureException ex) {
        String errorId = UUID.randomUUID().toString();
        String message = "Конкурентное обновление данных. Пожалуйста, повторите попытку.";
        return createErrorResponse(HttpStatus.CONFLICT, message, ex, errorId);
    }
}