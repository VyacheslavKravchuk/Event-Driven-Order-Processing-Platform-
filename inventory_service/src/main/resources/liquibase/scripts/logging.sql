CREATE TABLE reservation_log (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL, -- UUID из другого сервиса сохраняем как строку
    product_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
    -- Внешние ключи на другие сервисы (orders) удалены для автономности
);

-- Индексы для быстрой проверки existsBy...
CREATE INDEX idx_res_log_lookup ON reservation_log(order_id, product_id, status);

-- Индекс для аналитики по времени
CREATE INDEX idx_res_log_timestamp ON reservation_log(timestamp);

-- Уникальный индекс для строгой идемпотентности:
-- Не позволяет создать две записи "RESERVED" для одной пары Заказ-Товар
CREATE UNIQUE INDEX idx_res_log_unique_op ON reservation_log(order_id, product_id, status);