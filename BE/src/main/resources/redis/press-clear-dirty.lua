local cleared = 0
for index = 1, #ARGV, 2 do
    local score = redis.call('ZSCORE', KEYS[1], ARGV[index])
    if score and tonumber(score) <= tonumber(ARGV[index + 1]) then
        redis.call('ZREM', KEYS[1], ARGV[index])
        cleared = cleared + 1
    end
end
return cleared
