-- 1. Таблица журнала операций (WalletTransaction)
CREATE TABLE IF NOT EXISTS wallet_transactions (
    -- Используем transaction_id, чтобы совпадало с полем в сущности
    transaction_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- order_id теперь NULLABLE, так как при пополнении (DEPOSIT) заказа нет
    order_id         BIGINT,

    wallet_id        UUID NOT NULL,

    operation_type   VARCHAR(20) NOT NULL,

    -- Используем NUMERIC(19, 4) для высокой точности финансовых вычислений
    amount           NUMERIC(19, 4) NOT NULL,

    -- TIMESTAMP WITH TIME ZONE — лучший выбор для распределенных систем
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_transaction_wallet
        FOREIGN KEY (wallet_id)
        REFERENCES wallets(wallet_id)
        -- ON DELETE RESTRICT безопаснее для финансовых логов, чем CASCADE
        ON DELETE RESTRICT
);

-- 2. КЛЮЧЕВОЙ МОМЕНТ: Частичный уникальный индекс (Partial Unique Index)
-- Этот индекс гарантирует идемпотентность только для записей, где есть order_id.
-- Он проигнорирует обычные пополнения (где order_id IS NULL), позволяя им дублироваться по сумме/типу.
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_order_operation
ON wallet_transactions (order_id, operation_type)
WHERE order_id IS NOT NULL;

-- 3. Индексы для производительности
-- Индекс по wallet_id критичен для получения истории операций конкретного кошелька
CREATE INDEX IF NOT EXISTS idx_transactions_wallet_id
ON wallet_transactions(wallet_id);

-- Индекс по дате создания для быстрой сортировки истории (от новых к старым)
CREATE INDEX IF NOT EXISTS idx_transactions_created_at
ON wallet_transactions(created_at DESC);