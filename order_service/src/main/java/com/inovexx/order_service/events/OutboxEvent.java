package com.inovexx.order_service.events;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;  // ID заказа
    private String eventType;    // Например, "ORDER_CREATED"

    @Column(columnDefinition = "TEXT")
    private String payload;      // JSON данные заказа

    private LocalDateTime createdAt = LocalDateTime.now();
    private boolean processed = false; // Статус отправки
}

