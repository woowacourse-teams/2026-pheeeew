local time = redis.call('TIME')
local seconds = tonumber(time[1])
local micros = tonumber(time[2])
local nowMillis = seconds * 1000 + math.floor(micros / 1000)
local today = math.floor((seconds + 32400) / 86400)

if today ~= tonumber(ARGV[1]) then
    return { 'DATE_MISMATCH', seconds, micros, today }
end

if redis.call('EXISTS', KEYS[1]) == 0 then
    return { 'NOT_SEEDED', seconds, micros, today }
end

local topSize = tonumber(ARGV[3])
local nearbySize = tonumber(ARGV[4])
local leaderSize = tonumber(ARGV[5])
local activeWindowMillis = tonumber(ARGV[6])

local active = redis.call('ZCOUNT', KEYS[3], '(' .. tostring(nowMillis - activeWindowMillis), '+inf')

local size = redis.call('ZCARD', KEYS[2])
local index = false
local publicId = redis.call('HGET', KEYS[4], 'public_id')
if publicId then
    index = redis.call('ZRANK', KEYS[2], publicId .. ':' .. ARGV[2])
end

local nearbyStart = 0
local nearbyStop = leaderSize - 1
if index then
    nearbyStart = math.max(0, math.min(index - 1, math.max(size - nearbySize, 0)))
    nearbyStop = nearbyStart + nearbySize - 1
end
local nearby = redis.call('ZRANGE', KEYS[2], nearbyStart, nearbyStop, 'WITHSCORES')

local top = {}
if topSize > 0 then
    top = redis.call('ZRANGE', KEYS[2], 0, topSize - 1)
end

local nearbyFirstRank = 0
if #nearby > 0 then
    nearbyFirstRank = redis.call('ZCOUNT', KEYS[2], '-inf', '(' .. nearby[2]) + 1
end

local stateCount = #ARGV - 8
local rows = {}

local function appendRow(member)
    local device = string.sub(member, 38)
    local fields = redis.call('HMGET', ARGV[7] .. device, 'public_id', 'total', 'last_applied_at', unpack(ARGV, 9))
    if not fields[1] then
        return 0
    end
    table.insert(rows, device)
    table.insert(rows, fields[1])
    table.insert(rows, tonumber(fields[2]) or 0)
    table.insert(rows, tonumber(fields[3]) or 0)
    for field = 4, 3 + stateCount do
        table.insert(rows, tonumber(fields[field]) or 0)
    end
    table.insert(rows, redis.call('GET', ARGV[8] .. device))
    return 1
end

local nearbyCount = 0
for position = 1, #nearby, 2 do
    nearbyCount = nearbyCount + appendRow(nearby[position])
end

local topCount = 0
for _, member in ipairs(top) do
    topCount = topCount + appendRow(member)
end

local result = { 'READ', seconds, micros, today, active, nearbyStart, nearbyFirstRank, nearbyCount, topCount }
for _, cell in ipairs(rows) do
    table.insert(result, cell)
end
return result
