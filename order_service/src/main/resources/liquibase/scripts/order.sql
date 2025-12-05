CREATE TABLE orders (
    order_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_amount NUMERIC NOT NULL,
    order_date TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(255),
    version BIGINT,
    CONSTRAINT check_status CHECK (status IN ('NEW', 'RESERVED', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED')) -- Пример значений enum
);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    inventory_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    price NUMERIC NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(order_id)
);

