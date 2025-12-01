package com.inovexx.user_service.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.KafkaException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.stream.Collectors;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Обрабатывает исключение {@link UserNotFoundException}.
     * Возвращает HTTP статус 404 (NOT_FOUND) с сообщением об ошибке.
     *
     * @param ex Исключение {@link UserNotFoundException}.
     * @return Объект {@link ErrorResponse} с информацией об ошибке.
     */
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUsernameNotFoundException(UserNotFoundException ex) {
        return new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
    }

    /**
     * Обрабатывает исключение {@link DataIntegrityViolationException}.
     * Возвращает HTTP статус 409 (CONFLICT) с сообщением об ошибке о нарушении целостности данных.
     *
     * @param ex Исключение {@link DataIntegrityViolationException}.
     * @return Объект {@link ErrorResponse} с информацией об ошибке.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        return new ErrorResponse(HttpStatus.CONFLICT.value(), "Нарушение целостности данных: " + ex.getMessage());
    }

    /**
     * Обрабатывает исключение {@link MethodArgumentNotValidException}.
     * Возвращает HTTP статус 400 (BAD_REQUEST) со списком ошибок валидации.
     *
     * @param ex Исключение {@link MethodArgumentNotValidException}.
     * @return Объект {@link ErrorResponse} с информацией об ошибке валидации.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Ошибка валидации: " + String.join(", ", errors));
    }

    /**
     * Обрабатывает исключение {@link AccessDeniedException}.
     * Возвращает HTTP статус 403 (FORBIDDEN) с сообщением об отказе в доступе.
     *
     * @param ex Исключение {@link AccessDeniedException}.
     * @return Объект {@link ErrorResponse} с информацией об ошибке.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDeniedException(AccessDeniedException ex) {
        return new ErrorResponse(HttpStatus.FORBIDDEN.value(), "Доступ запрещен: " + ex.getMessage());
    }

    /**
     * Обрабатывает все необработанные исключения типа {@link Exception}.
     * Возвращает HTTP статус 500 (INTERNAL_SERVER_ERROR) с общим сообщением об ошибке.
     *
     * @param ex Исключение {@link Exception}.
     * @return Объект {@link ErrorResponse} с информацией об ошибке.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneralException(Exception ex) {
        logger.error("Непредвиденная ошибка: ", ex);
        return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Произошла непредвиденная ошибка.");
    }

    /**
     * Обрабатывает исключение {@link WebClientResponseException}, возникающее при ошибках вызова внешних сервисов через WebClient.
     * Возвращает HTTP статус, соответствующий статусу ошибки WebClient, и сообщение об ошибке.
     *
     * @param ex Исключение {@link WebClientResponseException}.
     * @return ResponseEntity с объектом {@link ErrorResponse} и соответствующим HTTP статусом.
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientResponseException(WebClientResponseException ex) {
        logger.error("Ошибка WebClient: ", ex);
        HttpStatus status = (HttpStatus) ex.getStatusCode(); // Приведение к HttpStatus
        ErrorResponse errorResponse = new ErrorResponse(status.value(), "Ошибка вызова сервиса: " + ex.getMessage());
        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * Обрабатывает исключение UserAlreadyExistsException, которое возникает,
     * когда делается попытка создать профиль пользователя с ID, который уже существует.
     *
     * @param ex Исключение UserAlreadyExistsException.
     * @return ResponseEntity с ErrorResponse и HTTP-статусом Conflict (409).
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserProfileAlreadyExistsException(UserAlreadyExistsException ex) {
        logger.error("Ошибка: Пользователь уже существует", ex);
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Обрабатывает исключение {@link JsonProcessingException}, возникающее при обработке JSON.
     * Возвращает HTTP статус 500 (INTERNAL SERVER ERROR) с сообщением об ошибке.
     *
     * @param ex Исключение {@link JsonProcessingException}.
     * @return ResponseEntity с JSON телом ответа об ошибке.
     */
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ErrorResponse> handleJsonProcessingException(JsonProcessingException ex) {
        logger.error("Ошибка при сериализации JSON для Kafka: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Ошибка обработки данных при подготовке сообщения в Kafka");

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Обработчик для общих исключений Kafka (например, недоступность брокера).
     * Возвращает HTTP статус 503 (SERVICE_UNAVAILABLE) с сообщением об ошибке.
     *
     * @param ex Исключение {@link KafkaException}.
     * @return ResponseEntity с JSON телом ответа об ошибке.
     */
    @ExceptionHandler(KafkaException.class)
    public ResponseEntity<ErrorResponse> handleKafkaException(KafkaException ex) {
        logger.error("Ошибка взаимодействия с Kafka: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(), // Статус 503 Service Unavailable
                "Сервис обмена сообщениями временно недоступен");

        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
