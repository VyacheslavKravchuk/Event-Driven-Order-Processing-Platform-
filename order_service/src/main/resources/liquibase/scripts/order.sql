-- =====================================================
-- 1. Таблица заказов
-- =====================================================
CREATE TABLE IF NOT EXISTS orders
(
    order_id                    UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     BIGINT                   NOT NULL,
    customer_email              VARCHAR(320)             NOT NULL,  -- RFC 5321
    total_amount                NUMERIC(19,2)            NOT NULL CHECK (total_amount >= 0),
    order_date                  TIMESTAMPTZ              NOT NULL DEFAULT NOW(),
    status                      VARCHAR(50)              NOT NULL,
    payment_status              VARCHAR(50)              NOT NULL DEFAULT 'NOT_CHARGED',
    cancel_reason               VARCHAR(500),
    cancelled_at                TIMESTAMPTZ,
    version                     BIGINT                   NOT NULL DEFAULT 0
);

-- Справочные ограничения статусов
ALTER TABLE orders
    ADD CONSTRAINT orders_status_check
        CHECK (status IN ('NEW','RESERVED','PAID','SHIPPED','COMPLETED','CANCELLED')),
    ADD CONSTRAINT orders_payment_status_check
        CHECK (payment_status IN ('NOT_CHARGED','CHARGED','REFUNDED','FAILED'));

--changeset order
-- =====================================================
-- 2. Таблица позиций заказа
-- =====================================================
CREATE TABLE IF NOT EXISTS order_items
(
    id                      BIGSERIAL PRIMARY KEY,
    order_id                UUID                     NOT NULL, -- ИСПРАВЛЕНО: BIGINT изменен на UUID для связи с orders
    product_id              VARCHAR(255)             NOT NULL,
    quantity                INTEGER                  NOT NULL CHECK (quantity > 0),
    price                   NUMERIC(19,2)            NOT NULL CHECK (price >= 0),
    reserved_quantity       INTEGER                  NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    reservation_released    BOOLEAN                  NOT NULL DEFAULT FALSE
);

-- FK с каскадным удалением только при удалении самого заказа
ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
        REFERENCES orders(order_id)
        ON DELETE CASCADE;

-- =====================================================
-- 3. Индексы для hot-путей
-- =====================================================
-- Поиск заказов пользователя
CREATE INDEX IF NOT EXISTS idx_orders_user_id
    ON orders(user_id);

-- Быстрый поиск по email (поддержка, админка)
CREATE INDEX IF NOT EXISTS idx_orders_customer_email
    ON orders(customer_email);

-- Фильтрация по статусу + дате (отчёты, фоновые джобы)
CREATE INDEX IF NOT EXISTS idx_orders_status_order_date
    ON orders(status, order_date DESC);

-- Получение состава заказа
CREATE INDEX IF NOT EXISTS idx_order_items_order_id
    ON order_items(order_id);

-- Поиск позиций по товару (актуально при инвентарных сверках)
CREATE INDEX IF NOT EXISTS idx_order_items_product_id
    ON order_items(product_id);

-- =====================================================
-- 4. Дополнительные полезные индексы
-- =====================================================
-- Заказы в статусе CANCELLED старше N дней (для архивной чистки)
CREATE INDEX IF NOT EXISTS idx_orders_cancelled_at
    ON orders(cancelled_at)
    WHERE status = 'CANCELLED';

-- Позиции, у которых reservation_released = false, но reserved_quantity > 0
-- (проверки «висячих» резервов)
CREATE INDEX IF NOT EXISTS idx_order_items_reservation_open
    ON order_items(order_id)
    WHERE reservation_released = FALSE AND reserved_quantity > 0;
