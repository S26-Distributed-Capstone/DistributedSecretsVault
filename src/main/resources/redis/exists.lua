local key = KEYS[1]
return redis.call('ZCARD', key)
