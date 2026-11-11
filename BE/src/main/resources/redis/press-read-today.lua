local time = redis.call('TIME')
local seconds = tonumber(time[1])
local micros = tonumber(time[2])
local today = math.floor((seconds + 32400) / 86400)

if today ~= tonumber(ARGV[1]) then
    return { 'DATE_MISMATCH', seconds, micros, today }
end

local token = redis.call('GET', KEYS[1])
if not token then
    return { 'NOT_SEEDED', seconds, micros, today }
end

local version = redis.call('GET', KEYS[2]) or '0'
local nicknameVersion = redis.call('GET', KEYS[3]) or '0'

if token == ARGV[2] and version == ARGV[3] and nicknameVersion == ARGV[4] then
    return { 'UNCHANGED', seconds, micros, today, token, version, nicknameVersion }
end

local result = { 'READ', seconds, micros, today, token, version, nicknameVersion }
local stateCount = #ARGV - 6
for _, member in ipairs(redis.call('ZRANGE', KEYS[4], 0, -1)) do
    local device = string.sub(member, 38)
    local fields = redis.call('HMGET', ARGV[5] .. device, 'public_id', 'total', 'last_applied_at', unpack(ARGV, 7))
    if fields[1] then
        table.insert(result, device)
        table.insert(result, fields[1])
        table.insert(result, tonumber(fields[2]) or 0)
        table.insert(result, tonumber(fields[3]) or 0)
        for index = 4, 3 + stateCount do
            table.insert(result, tonumber(fields[index]) or 0)
        end
        table.insert(result, redis.call('GET', ARGV[6] .. device))
    end
end
return result
