local time = redis.call('TIME')
local nowSeconds = tonumber(time[1])
local nowMillis = nowSeconds * 1000 + math.floor(tonumber(time[2]) / 1000)
local today = math.floor((nowSeconds + 32400) / 86400)

local function currentCounts(status)
    local result = { status, 0 }
    for index = 9, #ARGV, 2 do
        local count = redis.call('HGET', KEYS[2], ARGV[index])
        table.insert(result, tonumber(count) or 0)
    end
    return result
end

if today ~= tonumber(ARGV[1]) then
    return { 'DATE_MISMATCH', today }
end

if redis.call('EXISTS', KEYS[8]) == 0 then
    return { 'NOT_SEEDED', 0 }
end

local requestHash = ARGV[5]
if requestHash ~= '' then
    local stored = redis.call('GET', KEYS[7])
    if stored then
        if stored ~= requestHash then
            return { 'INPUT_CONFLICT', 0 }
        end
        return currentCounts('DUPLICATE')
    end
end

local applied = 0
for index = 10, #ARGV, 2 do
    applied = applied + tonumber(ARGV[index])
end

if applied > 0 then
    local capacity = tonumber(ARGV[6])
    local refillPerSecond = tonumber(ARGV[7])
    local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'refilled_at')
    local tokens = tonumber(bucket[1]) or capacity
    local refilledAt = tonumber(bucket[2]) or nowMillis
    local available = math.min(capacity, tokens + math.max(0, nowMillis - refilledAt) / 1000 * refillPerSecond)
    if available < applied then
        return { 'RATE_LIMITED', 0 }
    end

    local remaining = available - applied
    redis.call('HSET', KEYS[1], 'tokens', tostring(remaining), 'refilled_at', tostring(nowMillis))
    redis.call('PEXPIRE', KEYS[1], math.ceil((capacity - remaining) / refillPerSecond * 1000) + 1000)

    redis.call('HSETNX', KEYS[2], 'public_id', ARGV[4])
    local publicId = redis.call('HGET', KEYS[2], 'public_id')
    for index = 9, #ARGV, 2 do
        local count = tonumber(ARGV[index + 1])
        if count > 0 then
            redis.call('HINCRBY', KEYS[2], ARGV[index], count)
        end
    end
    redis.call('HINCRBY', KEYS[2], 'total', applied)
    redis.call('HSET', KEYS[2], 'last_applied_at', tostring(nowMillis))
    redis.call('ZADD', KEYS[9], nowMillis, ARGV[3])
    local version = redis.call('HINCRBY', KEYS[2], 'version', 1)

    redis.call('ZINCRBY', KEYS[3], -applied, publicId .. ':' .. ARGV[3])
    redis.call('INCRBY', KEYS[4], applied)
    redis.call('INCR', KEYS[5])
    redis.call('ZADD', KEYS[6], version, ARGV[2])
end

if requestHash ~= '' then
    redis.call('SET', KEYS[7], requestHash, 'PX', tonumber(ARGV[8]))
end

return currentCounts('APPLIED')
