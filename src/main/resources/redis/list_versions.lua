local key = KEYS[1]
local raw = redis.call('ZRANGE', key, 0, -1, 'WITHSCORES')
local versions = {}
for i = 2, #raw, 2 do
    table.insert(versions, raw[i])
end
return versions
