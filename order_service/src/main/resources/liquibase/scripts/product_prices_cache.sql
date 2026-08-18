-- Создание таблицы для кэширования данных о товарах из product-service
CREATE TABLE IF NOT EXISTS product_prices_cache (
    product_id VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    price NUMERIC(19, 2) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Первичный ключ по строковому ID товара (как в MongoDB)
    CONSTRAINT pk_product_prices_cache PRIMARY KEY (product_id),

    -- Ограничение, чтобы цена физически не могла быть отрицательной
    CONSTRAINT chk_product_price_positive CHECK (price >= 0)
);

-- Индекс для ускорения выборки, если в заказах будет выполняться поиск по имени
CREATE INDEX IF NOT EXISTS idx_product_cache_name ON product_prices_cache(name);

-- Комментарии к таблице и колонкам для документирования схемы БД
COMMENT ON TABLE product_prices_cache IS 'Локальный кэш товаров для независимого расчета стоимости заказов (обновляется асинхронно через Kafka)';
COMMENT ON COLUMN product_prices_cache.product_id IS 'Идентификатор товара, соответствующий ID из MongoDB сервиса продуктов';
COMMENT ON COLUMN product_prices_cache.price IS 'Актуальная стоимость товара в рублях';
COMMENT ON COLUMN product_prices_cache.updated_at IS 'Время последнего обновления информации о товаре из топика Kafka';