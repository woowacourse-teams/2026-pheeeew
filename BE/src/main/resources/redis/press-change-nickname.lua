redis.call('SET', KEYS[1], ARGV[1], 'PX', tonumber(ARGV[2]))
return redis.call('INCR', KEYS[2])
