local key = KEYS[1]
local version = ARGV[1]
local values = redis.call('ZRANGEBYSCORE', key, version, version, 'LIMIT', 0, 1)
if #values == 0 then
    return nil
end
return values[1]
