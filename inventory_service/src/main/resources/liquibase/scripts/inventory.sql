CREATE TABLE IF NOT EXISTS inventory (

    inventory_id BIGSERIAL PRIMARY KEY,

    product_id BIGINT NOT NULL,

    available_stock INTEGER NOT NULL DEFAULT 0 CHECK (available_stock >= 0),

    reserved_stock INTEGER NOT NULL DEFAULT 0 CHECK (reserved_stock >= 0),

    -- version: Поле для оптимистической блокировки. Используем BIGINT.
    version BIGINT NOT NULL DEFAULT 1
);

-- Дополнительно: часто полезно добавить уникальный индекс на product_id,
-- чтобы гарантировать, что у каждого продукта только одна запись в инвентаре.
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_product_id ON inventory (product_id);