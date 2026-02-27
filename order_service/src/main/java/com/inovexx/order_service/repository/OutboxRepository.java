package com.inovexx.order_service.repository;

import com.inovexx.order_service.events.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Стандартный поиск для простых сценариев.
     */
    List<OutboxEvent> findByProcessedFalseAndNextAttemptAtBefore(LocalDateTime time);

    /**
     * Безопасный выбор для планировщика (LockModeType.PESSIMISTIC_WRITE):
     * Блокирует строки в БД (SELECT ... FOR UPDATE), чтобы если у вас запущено
     * несколько экземпляров order-service, они не обрабатывали одни и те же события.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEvent e WHERE e.processed = false AND e.nextAttemptAt <= :now")
    List<OutboxEvent> findReadyForRetry(@Param("now") LocalDateTime now);

    /**
     * Находит все события, которые еще не были отправлены (без учета времени).
     */
    List<OutboxEvent> findByProcessedFalse();

    /**
     * Удаление старых, уже обработанных событий для очистки БД.
     */
    void deleteByProcessedTrue();
}
