-- Создание таблицы outbox
CREATE TABLE outbox (
    id BIGSERIAL PRIMARY KEY,  -- @Id @GeneratedValue(IDENTITY)
    order_id BIGINT,           -- orderId (nullable, как Long)
    event_type VARCHAR(255) NOT NULL,  -- eventType (FIXED strings, e.g. "ORDER_CREATED")
    payload TEXT NOT NULL,     -- @Column(columnDefinition = "TEXT")
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    role VARCHAR(20) NOT NULL,  -- @Enumerated(STRING)
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0  -- @Version optimistic locking
);

-- Создание индексов (как указано в @Table(indexes))
CREATE INDEX idx_outbox_processed_next_attempt ON outbox (processed, next_attempt_at);
CREATE INDEX idx_outbox_created_at ON outbox (created_at);

-- Опционально: комментарии для ясности
COMMENT ON TABLE outbox IS 'Outbox для событий (Transactional Outbox Pattern)';
COMMENT ON COLUMN outbox.id IS 'Уникальный ID события';
COMMENT ON COLUMN outbox.order_id IS 'ID aggregate root (заказ)';
COMMENT ON COLUMN outbox.event_type IS 'Тип события (не мутировать: ORDER_CREATED, START_GRPC_SAGA и т.д.)';
COMMENT ON COLUMN outbox.payload IS 'JSON snapshot события';
COMMENT ON COLUMN outbox.created_at IS 'Время создания';
COMMENT ON COLUMN outbox.processed IS 'Обработано ли событие';
COMMENT ON COLUMN outbox.status IS 'Статус: PENDING/RETRYING/SUCCESS/FAILED';
COMMENT ON COLUMN outbox.retry_count IS 'Количество попыток';
COMMENT ON COLUMN outbox.next_attempt_at IS 'Время следующей попытки';
COMMENT ON COLUMN outbox.version IS 'Версия для optimistic locking';
