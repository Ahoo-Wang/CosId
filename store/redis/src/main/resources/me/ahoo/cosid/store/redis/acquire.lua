-- SlotLeaseStore.acquire (05-machine-lease.md §2.1, 06-stores.md §2.2), atomic as one script.
-- KEYS: cosid:{ns}:meta, :slot, :free, :live, :owner, :held.
-- ARGV: limit, toleranceMillis, ttlMillis, ownerKey, incarnation, preferredSlot ('' for none), sweep batch.
-- Every slot below the limit is in exactly one of the sorted sets: free (score = slot) or live (score = expiresAt).
local metaKey, slotKey, freeKey, liveKey, ownerKey, heldKey = KEYS[1], KEYS[2], KEYS[3], KEYS[4], KEYS[5], KEYS[6]
local limit = tonumber(ARGV[1])
local tolerance = tonumber(ARGV[2])
local ttl = tonumber(ARGV[3])
local key, inc, preferred = ARGV[4], ARGV[5], ARGV[6]

local meta = redis.call('HMGET', metaKey, 'limit', 'tolerance')
if not meta[1] then
    redis.call('HSET', metaKey, 'limit', ARGV[1], 'tolerance', ARGV[2])
    local batch = {}
    for slot = 0, limit - 1 do
        batch[#batch + 1] = slot
        batch[#batch + 1] = slot
        if #batch >= 1000 or slot == limit - 1 then
            redis.call('ZADD', freeKey, 'NX', unpack(batch))
            batch = {}
        end
    end
elseif meta[1] ~= ARGV[1] or meta[2] ~= ARGV[2] then
    return {'mismatch', meta[1], meta[2]}
end

local now = nowMillis()
-- A bounded sweep: at most ARGV[7] expired leases move to free, so acquire stays O(log n) however many expired.
local expired = redis.call('ZRANGEBYSCORE', liveKey, '-inf', '(' .. num(now), 'LIMIT', 0, tonumber(ARGV[7]))
for _, slot in ipairs(expired) do
    redis.call('ZREM', liveKey, slot)
    redis.call('ZADD', freeKey, slot, slot)
end

local function isFree(slot)
    local expiresAt = redis.call('ZSCORE', liveKey, slot)
    return not expiresAt or tonumber(expiresAt) < now
end

local duplicate = '0'
local mine
local held = redis.call('HGET', heldKey, key)
if held then
    for slot in string.gmatch(held, '[^,]+') do
        local row = decode(redis.call('HGET', slotKey, slot))
        if row.inc == inc then
            mine = slot
        elseif row.expiresAt >= now then
            duplicate = '1'
        end
    end
end

local function reply(slot, row, reason)
    return {'acquired', reason, duplicate, slot, num(row.fence), num(row.expiresAt), num(row.floor), num(row.ceiling), num(now)}
end

local function take(slot, reason)
    local row = decode(redis.call('HGET', slotKey, slot))
    if row.inc ~= '' then
        heldRemove(heldKey, row.key, slot)
    end
    row.fence = row.fence + 1
    row.key = key
    row.inc = inc
    row.last = key
    row.floor = row.nextFloor
    row.expiresAt = now + ttl
    row.ceiling = math.max(row.expiresAt + tolerance, row.floor)
    row.nextFloor = row.ceiling
    redis.call('HSET', slotKey, slot, encode(row))
    redis.call('ZREM', freeKey, slot)
    redis.call('ZADD', liveKey, num(row.expiresAt), slot)
    redis.call('HSET', ownerKey, key, slot)
    heldAdd(heldKey, key, slot)
    return reply(slot, row, reason)
end

-- 1. Held by this very owner (key and incarnation): a retry, returned as it is.
if mine then
    return reply(mine, decode(redis.call('HGET', slotKey, mine)), 'IDEMPOTENT')
end
-- 2. The preferred slot.
if preferred ~= '' then
    local slot = tonumber(preferred)
    if slot >= 0 and slot < limit and isFree(preferred) then
        return take(preferred, 'PREFERRED')
    end
end
-- 3. The slot this owner key took last, if nobody else took it since.
local last = redis.call('HGET', ownerKey, key)
if last and tonumber(last) < limit and isFree(last) and decode(redis.call('HGET', slotKey, last)).last == key then
    return take(last, 'STICKY')
end
-- 4. The lowest free slot. The sweep ran first, so an expired lease, if any, is among the free slots.
local lowest = redis.call('ZRANGEBYSCORE', freeKey, 0, limit - 1, 'LIMIT', 0, 1)
if lowest[1] then
    return take(lowest[1], 'LOWEST')
end
-- 5. Exhausted: every slot is live, and the earliest expiry is still ahead.
local earliest = redis.call('ZRANGE', liveKey, 0, 0, 'WITHSCORES')
return {'exhausted', num(tonumber(earliest[2]))}
