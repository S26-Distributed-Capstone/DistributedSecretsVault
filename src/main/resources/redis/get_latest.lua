local key = KEYS[1]
local values = redis.call('ZREVRANGE', key, 0, 0)
if #values == 0 then
    return nil
end
return values[1]
