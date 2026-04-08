package com.inovexx.order_service.repository;

import com.inovexx.order_service.events.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Безопасный выбор для планировщика (LockModeType.PESSIMISTIC_WRITE):
     * Блокирует строки в БД (SELECT ... FOR UPDATE), чтобы если у вас запущено
     * несколько экземпляров order-service, они не обрабатывали одни и те же события.
     */
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEvent e WHERE e.processed = false AND e.nextAttemptAt <= :now")
    List<OutboxEvent> findReadyForRetry(@Param("now") OffsetDateTime now);

    //  Добавляем @Modifying и @Transactional (для удаления)
    @Modifying
    @Transactional
    void deleteByProcessedTrue();
    // Упрощаем - Spring сам поймет этот метод по имени, @Query не нужен
    long countByProcessedFalse();

    // Исправляем метод для пачек (Batch)
    // Убираем ORDER BY из основного запроса, если используете Pageable (сортировку лучше передавать в Pageable)
    // Но если оставляем в @Query, убедимся, что в Pageable сортировка пустая.
    @Query("SELECT e FROM OutboxEvent e WHERE e.processed = false AND e.nextAttemptAt <= :now")
    List<OutboxEvent> findEventsToProcess(@Param("now") OffsetDateTime now, Pageable pageable);

    //  Пессимистичная блокировка
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select e
    from OutboxEvent e
    where e.id = :id
      and e.processed = false
      and (e.nextAttemptAt is null or e.nextAttemptAt <= CURRENT_TIMESTAMP)
""")
    Optional<OutboxEvent> findByIdForProcessing(@Param("id") Long id);

    @Modifying
    @Transactional
    void deleteByProcessedTrueAndCreatedAtBefore(OffsetDateTime threshold);

}
