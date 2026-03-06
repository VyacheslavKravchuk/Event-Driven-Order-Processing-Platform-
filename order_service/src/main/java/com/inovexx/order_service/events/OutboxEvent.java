package com.inovexx.order_service.events;

import com.inovexx.order_service.enums.EventStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "outbox", indexes = {
        @Index(name = "idx_outbox_proc_next", columnList = "processed, next_attempt_at"),
        @Index(name = "idx_outbox_created", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false) // updatable=false защищает от случайной смены ID
    private Long orderId;

    @Column(nullable = false, updatable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private boolean processed = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status = EventStatus.PENDING;

    @Column(nullable = false)
    private Integer retryCount = 0;
    // Важно: если в индексе указано next_attempt_at,
    // убедитесь что JPA маппит его именно так (по умолчанию так и есть)
    @Column(nullable = false)
    private OffsetDateTime nextAttemptAt = OffsetDateTime.now();

    @Version
    private Long version;

}