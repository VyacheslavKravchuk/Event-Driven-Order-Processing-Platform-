--liquibase formatted sql

--changeset order:1
CREATE TABLE IF NOT EXISTS orders (
    order_id     BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL, -- точность для денег
    order_date   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status       VARCHAR(50) NOT NULL,
    version      BIGINT DEFAULT 0,     -- Для Optimistic Locking в JPA
    CONSTRAINT check_status CHECK (status IN ('NEW', 'RESERVED', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED'))
);

--changeset order:2
CREATE TABLE IF NOT EXISTS order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    quantity   INTEGER NOT NULL CHECK (quantity > 0), -- Валидация на уровне БД
    price      NUMERIC(19, 2) NOT NULL,
    -- ON DELETE CASCADE удалит позиции, если удалится сам заказ
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES orders(order_id) ON DELETE CASCADE
);

--changeset order:3
-- Индекс для поиска заказов конкретного пользователя (частый запрос)
CREATE INDEX idx_orders_user_id ON orders(user_id);

-- Индекс для внешнего ключа (ускоряет JOIN при получении состава заказа)
CREATE INDEX idx_order_items_order_id ON order_items(order_id);