--liquibase formatted sql

--changeset outbox:v2
CREATE TABLE IF NOT EXISTS outbox (
    id                BIGSERIAL PRIMARY KEY,
    -- Маппинг для orderId (Long)
    order_id          UUID NOT NULL,
    -- Тип события (ORDER_CREATED, etc)
    event_type        VARCHAR(255) NOT NULL,
    -- JSON данные (TEXT в Postgres соответствует String в JPA)
    payload           TEXT NOT NULL,

    -- Даты с часовым поясом (OffsetDateTime)
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    next_attempt_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at      TIMESTAMP WITH TIME ZONE,
    last_attempt_at   TIMESTAMP WITH TIME ZONE,

    -- Состояние и обработка
    processed         BOOLEAN NOT NULL DEFAULT FALSE,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count       INTEGER NOT NULL DEFAULT 0,

    -- Логирование ошибок
    last_error_message TEXT,

    -- Оптимистическая блокировка JPA (@Version)
    version           BIGINT NOT NULL DEFAULT 0
);

--changeset outbox:v2_indexes
-- Индекс 1: Поиск для планировщика (processed + next_attempt_at)
-- Название индекса в точности как в @Index(name = "idx_outbox_proc_next")
CREATE INDEX idx_outbox_proc_next ON outbox (processed, next_attempt_at);

-- Индекс 2: Поиск по времени создания (@Index(name = "idx_outbox_created"))
CREATE INDEX idx_outbox_created ON outbox (created_at);

-- Индекс 3: Индекс по order_id (полезен для поиска всех событий конкретного заказа)
CREATE INDEX idx_outbox_order_id ON outbox (order_id);

-- Добавляем комментарии (Best Practice для эксплуатации БД)
COMMENT ON COLUMN outbox.status IS 'Статусы из EventStatus: PENDING, PROCESSED, FAILED, etc';
COMMENT ON COLUMN outbox.last_error_message IS 'Текст ошибки последней попытки отправки в Kafka';