-- TargetAdmin.raise on the 5.0 Redis store (06-stores.md §2.2). KEYS: cosid:{key}, cosid:{key}:sealed. ARGV: next.
-- The values stay strings, compared as non-negative decimals (length first): Lua numbers would round above 2^53.
local current = redis.call('GET', KEYS[1])
local function less(a, b)
    if #a ~= #b then
        return #a < #b
    end
    return a < b
end
if not current or less(current, ARGV[1]) then
    redis.call('SET', KEYS[1], ARGV[1])
    current = ARGV[1]
end
return {current, tostring(redis.call('EXISTS', KEYS[2]))}
