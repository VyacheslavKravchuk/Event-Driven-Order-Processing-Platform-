-- KEYS[1] - ключ в Redis (например, rate_limit:user123)
-- ARGV[1] - максимальная емкость ведра (bucketCapacity)
-- ARGV[2] - скорость пополнения в секунду (refillRate)
-- ARGV[3] - текущее время в секундах (currentTime)

local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local currentTime = tonumber(ARGV[3])

-- HGETALL для получения сохраненных данных (tokens, timestamp)
local data = redis.call('HGETALL', KEYS[1])
local lastTokens = capacity
local lastTimestamp = currentTime

if #data > 0 then
    -- Если данные уже есть, парсим их
    for i=1, #data, 2 do
        if data[i] == 'tokens' then lastTokens = tonumber(data[i+1]) end
        if data[i] == 'timestamp' then lastTimestamp = tonumber(data[i+1]) end
    end
end

-- Рассчитываем, сколько времени прошло и сколько токенов добавилось
local timePassed = math.max(0, currentTime - lastTimestamp)
local newTokens = math.min(capacity, lastTokens + (timePassed * rate))

-- Проверяем, достаточно ли у нас токенов для текущего запроса (мы запрашиваем 1)
if newTokens >= 1 then
    -- Если да, уменьшаем количество токенов и обновляем время
    newTokens = newTokens - 1
    redis.call('HMSET', KEYS[1], 'tokens', newTokens, 'timestamp', currentTime)
    redis.call('EXPIRE', KEYS[1], 60) -- Устанавливаем TTL для ключа
    return 1 -- Разрешаем запрос
else
    -- Если нет, просто обновляем время и токены (не уменьшаем)
    redis.call('HMSET', KEYS[1], 'tokens', newTokens, 'timestamp', currentTime)
    redis.call('EXPIRE', KEYS[1], 60)
    return 0 -- Отклоняем запрос
end
