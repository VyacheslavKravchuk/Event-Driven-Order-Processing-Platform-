package com.inovexx.order_service.repository;

import com.inovexx.order_service.events.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Находит все события, которые еще не были отправлены в Kafka.
     */
    List<OutboxEvent> findByProcessedFalse();

    /**
     * Вариант для высокой нагрузки (High Availability):
     * Находит необработанные события и блокирует их (SELECT FOR UPDATE),
     * чтобы другой экземпляр сервиса не взял их в работу параллельно.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEvent e WHERE e.processed = false")
    List<OutboxEvent> findUnprocessedForUpdate();

    /**
     * Удаление старых, уже обработанных событий (опционально, для очистки БД)
     */
    void deleteByProcessedTrue();
}
