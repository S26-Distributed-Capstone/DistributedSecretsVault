local key = KEYS[1]
local version = ARGV[1]
local payload = ARGV[2]
if redis.call('ZCARD', key) == 0 then
    return 0
end
redis.call('ZREMRANGEBYSCORE', key, version, version)
redis.call('ZADD', key, version, payload)
return 1
