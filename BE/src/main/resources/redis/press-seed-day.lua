if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end
redis.call('HSET', KEYS[1], 'public_id', ARGV[1], 'total', ARGV[2], 'last_applied_at', ARGV[3], 'version', ARGV[4])
for index = 6, #ARGV, 2 do
    redis.call('HSET', KEYS[1], ARGV[index], ARGV[index + 1])
end
redis.call('ZADD', KEYS[2], -tonumber(ARGV[2]), ARGV[1] .. ':' .. ARGV[5])
redis.call('ZADD', KEYS[4], tonumber(ARGV[3]), ARGV[5])
redis.call('INCRBY', KEYS[3], tonumber(ARGV[2]))
return 1
