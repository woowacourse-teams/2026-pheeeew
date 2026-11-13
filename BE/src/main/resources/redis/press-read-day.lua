local time = redis.call('TIME')
local today = math.floor((tonumber(time[1]) + 32400) / 86400)

if today ~= tonumber(ARGV[1]) then
    return { 'DATE_MISMATCH', today }
end

if redis.call('EXISTS', KEYS[1]) == 0 then
    return { 'NOT_SEEDED', today }
end

local result = { 'READ', today, tonumber(redis.call('GET', KEYS[2])) or 0 }
if #KEYS >= 3 then
    for index = 2, #ARGV do
        table.insert(result, tonumber(redis.call('HGET', KEYS[3], ARGV[index])) or 0)
    end
end
return result
