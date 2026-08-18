CREATE TABLE IF NOT EXISTS inventory (
    -- Внутренний ID записи в PostgreSQL
    inventory_id BIGSERIAL PRIMARY KEY,

    -- ID продукта из MongoDB (внешняя система), теперь это строка
    product_id VARCHAR(255) NOT NULL,

    -- Остатки: добавлена проверка, чтобы не уходить в минус
    available_stock INTEGER NOT NULL DEFAULT 0 CONSTRAINT check_available_stock_positive CHECK (available_stock >= 0),

    -- Резерв: добавлена проверка
    reserved_stock INTEGER NOT NULL DEFAULT 0 CONSTRAINT check_reserved_stock_positive CHECK (reserved_stock >= 0),

    -- Поле для оптимистической блокировки Hibernate (@Version)
    -- Обычно Hibernate начинает отсчет с 0
    version BIGINT NOT NULL DEFAULT 0
);

-- Уникальный индекс обязателен:
-- 1. Гарантирует, что для одного товара из Mongo будет только одна запись в остатках.
-- 2. Ускоряет поиск (SELECT/UPDATE) по productId, который делает Kafka-слушатель и gRPC.
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_product_id ON inventory (product_id);
