-- RangeStore.reserve (06-stores.md §2.2). KEYS: cosid:{key}, cosid:{key}:sealed. ARGV: size, initialOffset.
-- Arguments and results stay strings: Lua numbers are doubles and would round values above 2^53, so the counter is
-- only touched by SETNX / INCRBY (64-bit integers in Redis) and read back with GET.
if redis.call('EXISTS', KEYS[2]) == 1 then
    return {'sealed'}
end
redis.call('SETNX', KEYS[1], ARGV[2])
local added = redis.pcall('INCRBY', KEYS[1], ARGV[1])
if type(added) == 'table' and added.err then
    if string.find(added.err, 'overflow', 1, true) then
        return {'exhausted'}
    end
    return redis.error_reply(added.err)
end
return {'reserved', redis.call('GET', KEYS[1])}
