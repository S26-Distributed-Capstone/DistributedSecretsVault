local key = KEYS[1]
local version = ARGV[1]
local payload = ARGV[2]
redis.call('ZREMRANGEBYSCORE', key, version, version)
redis.call('ZADD', key, version, payload)
return 1
