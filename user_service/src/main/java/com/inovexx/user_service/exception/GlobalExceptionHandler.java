package com.inovexx.user_service.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.kafka.KafkaException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;


import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Глобальный обработчик исключений для приложения.
 * Перехватывает исключения, возникающие в различных частях приложения, и преобразует их в стандартизированные
 * ответы об ошибках в формате JSON.  Использует аннотации {@link ControllerAdvice} и {@link ExceptionHandler}
 * для обработки исключений централизованно.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Обрабатывает исключение {@link UserNotFoundException}, которое выбрасывается, когда пользователь не найден.
     * Возвращает HTTP статус 404 (NOT_FOUND) с сообщением об ошибке.
     *
     * @param ex Исключение {@link UserNotFoundException}.
     * @return Объект {@link ErrorResponse} с информацией об ошибке.
     */
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUsernameNotFoundException(UserNotFoundException ex) {
        log.warn("Пользователь не найден: {}", ex.getMessage()); // Используем SLF4J logger
        return new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
    }

    /**
     * Обрабатывает исключение {@link DataIntegrityViolationException}, которое возникает при нарушении ограничений целостности данных (например, уникальности).
     * Возвращает HTTP статус 409 (CONFLICT) с сообщением об ошибке о нарушении целостности данных.
     *
     * @param ex Исключение {@link DataIntegrityViolationException}.
     * @return Объект {@link ErrorResponse} с информацией об ошибке.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.error("Нарушение целостности данных: {}", ex.getMessage());
        return new ErrorResponse(HttpStatus.CONFLICT.value(), "Нарушение целостности данных: " + ex.getMessage());
    }

    /**
     * Обрабатывает исключение {@link MethodArgumentNotValidException}, которое возникает при неудачной валидации аргументов метода, помеченных аннотацией {@link Validated}.
     * Возвращает HTTP статус 400 (BAD_REQUEST) со списком ошибок валидации.
     *
     * @param ex Исключение {@link MethodArgumentNotValidException}.
     * @return {@link ResponseEntity} с объектом {@link ErrorResponse} и информацией об ошибках валидации.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> Objects.requireNonNullElse(fieldError.getDefaultMessage(), ""), // Обработка null
                        (existing, replacement) -> existing // Функция слияния (в данном случае просто берем существующее значение)
                ));
        log.warn("Ошибка валидации: {}", errors);

        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Ошибка валидации", errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает исключение {@link AccessDeniedException}, которое возникает, когда у пользователя нет прав доступа к запрашиваемому ресурсу.
     * Возвращает HTTP статус 403 (FORBIDDEN) с сообщением об отказе в доступе.
     *
     * @param ex Исключение {@link AccessDeniedException}.
     * @return Объект {@link ErrorResponse} с информацией об ошибке.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Доступ запрещен: {}", ex.getMessage());
        return new ErrorResponse(HttpStatus.FORBIDDEN.value(), "Доступ запрещен: " + ex.getMessage());
    }

    /**
     * Обрабатывает все необработанные исключения типа {@link Exception}.  Рекомендуется добавлять более специфичные обработчики для конкретных типов исключений.
     * Возвращает HTTP статус 500 (INTERNAL_SERVER_ERROR) с общим сообщением об ошибке.
     *
     * @param ex Исключение {@link Exception}.
     * @return Объект {@link ErrorResponse} с информацией об ошибке.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneralException(Exception ex) {
        log.error("Непредвиденная ошибка: ", ex);
        return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Произошла непредвиденная ошибка.");
    }

    /**
     * Обрабатывает исключение {@link WebClientResponseException}, возникающее при ошибках вызова внешних сервисов через {@link WebClient}.
     * Возвращает HTTP статус, соответствующий статусу ошибки WebClient, и сообщение об ошибке.
     *
     * @param ex Исключение {@link WebClientResponseException}.
     * @return {@link ResponseEntity} с объектом {@link ErrorResponse} и соответствующим HTTP статусом.
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleWebClientResponseException(WebClientResponseException ex) {
        log.error("Ошибка WebClient: Статус={}, Тело={}", ex.getStatusCode(), ex.getResponseBodyAsString(), ex); // Логируем тело ответа
        HttpStatus status = (HttpStatus) ex.getStatusCode(); // Приведение к HttpStatus
        ErrorResponse errorResponse = new ErrorResponse(status.value(), "Ошибка вызова сервиса: " + ex.getMessage());
        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * Обрабатывает исключение {@link UserAlreadyExistsException}, которое возникает, когда делается попытка создать пользователя с уже существующим идентификатором или именем пользователя.
     * Возвращает HTTP статус 409 (CONFLICT) с сообщением об ошибке.
     *
     * @param ex Исключение {@link UserAlreadyExistsException}.
     * @return {@link ResponseEntity} с объектом {@link ErrorResponse} и HTTP статусом Conflict (409).
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserProfileAlreadyExistsException(UserAlreadyExistsException ex) {
        log.warn("Пользователь уже существует: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Обрабатывает исключение {@link JsonProcessingException}, возникающее при проблемах с сериализацией или десериализацией JSON, например, при отправке сообщений в Kafka.
     * Возвращает HTTP статус 500 (INTERNAL SERVER ERROR) с сообщением об ошибке.
     *
     * @param ex Исключение {@link JsonProcessingException}.
     * @return {@link ResponseEntity} с объектом {@link ErrorResponse} и HTTP статусом Internal Server Error (500).
     */
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ErrorResponse> handleJsonProcessingException(JsonProcessingException ex) {
        log.error("Ошибка при сериализации JSON для Kafka: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Ошибка обработки данных при подготовке сообщения в Kafka");

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Обработчик для общих исключений Kafka (например, недоступность брокера), возникающих при взаимодействии с Kafka.
     * Возвращает HTTP статус 503 (SERVICE_UNAVAILABLE) с сообщением об ошибке.
     *
     * @param ex Исключение {@link KafkaException}.
     * @return {@link ResponseEntity} с объектом {@link ErrorResponse} и HTTP статусом Service Unavailable (503).
     */
    @ExceptionHandler(KafkaException.class)
    public ResponseEntity<ErrorResponse> handleKafkaException(KafkaException ex) {
        log.error("Ошибка взаимодействия с Kafka: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(), // Статус 503 Service Unavailable
                "Сервис обмена сообщениями временно недоступен");

        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Обрабатывает исключение {@link WalletRegisteredNotFoundException}, возникающее, когда зарегистрированный кошелек не найден.
     * Возвращает HTTP статус 404 (NOT_FOUND) с сообщением об ошибке.
     *
     * @param e Исключение {@link WalletRegisteredNotFoundException}.
     * @return {@link ResponseEntity} с объектом {@link ErrorResponse} и HTTP статусом Not Found (404).
     */
    @ExceptionHandler(WalletRegisteredNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(WalletRegisteredNotFoundException e) {
        log.warn("Ресурс не найден: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Обрабатывает исключение {@link IllegalArgumentWalletException}, возникающее, когда предоставлены некорректные аргументы для работы с кошельком.
     * Возвращает HTTP статус 400 (BAD_REQUEST) с сообщением об ошибке.
     *
     * @param e Исключение {@link IllegalArgumentWalletException}.
     * @return {@link ResponseEntity} с объектом {@link ErrorResponse} и HTTP статусом Bad Request (400).
     */
    @ExceptionHandler(IllegalArgumentWalletException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentWalletException e) {
        log.warn("Ошибка в параметрах запроса: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает исключение {@link WalletRequestNotFoundException}, возникающее, когда запрос кошелька не найден.
     * Возвращает HTTP статус 404 (NOT_FOUND) с сообщением об ошибке.
     *
     * @param e Исключение {@link WalletRequestNotFoundException}.
     * @return {@link ResponseEntity} с объектом {@link ErrorResponse} и HTTP статусом Not Found (404).
     */
    @ExceptionHandler(WalletRequestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransactionNotFound(WalletRequestNotFoundException e) {
        log.warn("Транзакция не найдена: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Обрабатывает исключение {@link InsufficientFundsException}, возникающее, когда на счету недостаточно средств для выполнения операции.
     * Возвращает HTTP статус 400 (BAD_REQUEST) с сообщением об ошибке.
     *
     * @param e Исключение {@link InsufficientFundsException}.
     * @return {@link ResponseEntity} с объектом {@link ErrorResponse} и HTTP статусом Bad Request (400).
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e) {
        log.warn("Ошибка проведения операции: {}", e.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "На счету недостаточно средств для выполнения операции");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Обрабатывает исключение {@link HttpMessageNotReadableException}, возникающее при невозможности чтения HTTP сообщения (например, невалидный JSON).
     * Возвращает HTTP статус 400 (BAD_REQUEST) с сообщением об ошибке.
     *
     * @param ex Исключение {@link HttpMessageNotReadableException}.
     * @return {@link ResponseEntity} с объектом {@link ErrorResponse} и HTTP статусом Bad Request (400).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Невозможно прочитать HTTP сообщение: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Некорректный запрос: Невозможно прочитать сообщение");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

}

