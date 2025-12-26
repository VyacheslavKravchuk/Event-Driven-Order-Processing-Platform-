-- Таблица зарегистрированных кошельков
CREATE TABLE IF NOT EXISTS wallets (
    wallet_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Баланс в минимальных единицах (например, копейки), чтобы избежать ошибок округления
    balance NUMERIC NOT NULL DEFAULT 0,
    -- Связь 1-к-1 с пользователем: один пользователь — один кошелек
    user_id BIGINT NOT NULL UNIQUE,
    -- Email для удобства (опционально, т.к. он есть в users)
    email VARCHAR(255) NOT NULL,

    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);