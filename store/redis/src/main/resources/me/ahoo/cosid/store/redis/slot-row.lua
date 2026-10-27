-- Shared by the slot scripts (06-stores.md §2.2), prepended to each of them.
-- A row of the cosid:{ns}:slot hash is "ownerKey|incarnation|lastOwnerKey|fence|expiresAt|floor|ceiling|nextFloor";
-- owner keys are escaped ('\' as '\\', '|' as '\p'), a free slot has an empty owner key and incarnation. Every value
-- here is a millisecond time or a counter, well below 2^53, so Lua numbers hold them exactly.
local function escape(text)
    return (string.gsub(string.gsub(text, '\\', '\\\\'), '|', '\\p'))
end

local function unescape(text)
    return (string.gsub(text, '\\(.)', function(c)
        if c == 'p' then
            return '|'
        end
        return c
    end))
end

local function num(value)
    return string.format('%.0f', value)
end

local function decode(raw)
    if not raw then
        return {key = '', inc = '', last = '', fence = 0, expiresAt = 0, floor = 0, ceiling = 0, nextFloor = 0}
    end
    local f = {}
    for part in string.gmatch(raw .. '|', '([^|]*)|') do
        f[#f + 1] = part
    end
    return {key = unescape(f[1]), inc = f[2], last = unescape(f[3]), fence = tonumber(f[4]), expiresAt = tonumber(f[5]),
            floor = tonumber(f[6]), ceiling = tonumber(f[7]), nextFloor = tonumber(f[8])}
end

local function encode(row)
    return table.concat({escape(row.key), row.inc, escape(row.last), num(row.fence), num(row.expiresAt), num(row.floor),
                         num(row.ceiling), num(row.nextFloor)}, '|')
end

-- The store clock (05 §3): Redis 6+ replicates script effects, so TIME is safe in a writing script.
local function nowMillis()
    local time = redis.call('TIME')
    return tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
end

-- cosid:{ns}:held: ownerKey -> the comma separated slots whose row names that owner key, live or expired.
local function heldRemove(heldKey, ownerKey, slot)
    local list = redis.call('HGET', heldKey, ownerKey)
    if not list then
        return
    end
    local kept = {}
    for held in string.gmatch(list, '[^,]+') do
        if held ~= slot then
            kept[#kept + 1] = held
        end
    end
    if #kept == 0 then
        redis.call('HDEL', heldKey, ownerKey)
    else
        redis.call('HSET', heldKey, ownerKey, table.concat(kept, ','))
    end
end

local function heldAdd(heldKey, ownerKey, slot)
    local list = redis.call('HGET', heldKey, ownerKey)
    if list then
        redis.call('HSET', heldKey, ownerKey, list .. ',' .. slot)
    else
        redis.call('HSET', heldKey, ownerKey, slot)
    end
end

