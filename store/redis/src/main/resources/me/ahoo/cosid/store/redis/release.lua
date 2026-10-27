-- SlotLeaseStore.release (05-machine-lease.md §2.2). KEYS: cosid:{ns}:slot, :free, :live, :held.
-- ARGV: slot, incarnation, fence, lastIssued (any long; compared as a double, which only rounds beyond the ceiling).
local slotKey, freeKey, liveKey, heldKey = KEYS[1], KEYS[2], KEYS[3], KEYS[4]
local slot = ARGV[1]
local raw = redis.call('HGET', slotKey, slot)
if not raw then
    return {'lost'}
end
local row = decode(raw)
if row.inc == '' or row.inc ~= ARGV[2] or row.fence ~= tonumber(ARGV[3]) then
    return {'lost'}
end
heldRemove(heldKey, row.key, slot)
row.key = ''
row.inc = ''
row.expiresAt = nowMillis()
row.nextFloor = math.max(row.floor, math.min(tonumber(ARGV[4]), row.ceiling))
row.fence = row.fence + 1
redis.call('HSET', slotKey, slot, encode(row))
redis.call('ZREM', liveKey, slot)
redis.call('ZADD', freeKey, slot, slot)
return {'released'}
