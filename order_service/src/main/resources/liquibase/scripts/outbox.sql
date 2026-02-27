--liquibase formatted sql
-- Создание таблицы для паттерна Outbox
--changeset outbox:1
CREATE TABLE IF NOT EXISTS outbox (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL,
    event_type          VARCHAR(100) NOT NULL,
    payload             TEXT NOT NULL, -- JSON данные заказа
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Поля для планировщика и ретраев
    processed           BOOLEAN DEFAULT FALSE,
    retry_count         INTEGER DEFAULT 0,
    next_attempt_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Индекс для планировщика (самый важный)
-- Позволяет мгновенно находить только те записи, которые нужно отправить прямо сейчас
--changeset outbox:2
CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed_retry
ON outbox (processed, next_attempt_at)
WHERE processed = FALSE;

-- Индекс по order_id для быстрого поиска истории событий по конкретному заказу
--changeset outbox:3
CREATE INDEX IF NOT EXISTS idx_outbox_order_id ON outbox (order_id);

-- Комментарии к колонкам для документации БД
--changeset outbox:4
COMMENT ON COLUMN outbox.payload IS 'Данные события в формате JSON';
COMMENT ON COLUMN outbox.processed IS 'Флаг успешной отправки в Kafka';
COMMENT ON COLUMN outbox.next_attempt_at IS 'Время следующей попытки при сбое';
