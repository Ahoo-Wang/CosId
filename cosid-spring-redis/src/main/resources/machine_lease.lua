-- MachineLeaseStore primitives on the same keys as machine_id_distribute.lua, so both can serve one namespace:
--   cosid:{namespace}:itc_idx  [Hash]   instanceId -> "machineId|lastTimestamp"
--   cosid:{namespace}:revert   [Hash]   machineId  -> lastTimestamp (released machine ids)
--   cosid:{namespace}:adder    [String] last machine id ever handed out (-1 when none)
--   cosid:{namespace}:stable   [Set]    instance ids of stable instances, never recycled
-- Every write sets the last timestamp to max(stored + 1, now): it strictly increases and is the lease version.
-- Integers are formatted with %d: tostring would turn large numbers into floats ("1.7e+12").
--
-- KEYS[1] = namespace (hash tag); ARGV[1] = operation, ARGV[2] = instanceId, ARGV[3] = stable ("1"/"0"), then:
--   claimSelf     ARGV[4] = now
--   claimReleased ARGV[4] = now, ARGV[5] = maxMachineId
--   claimNew      ARGV[4] = now, ARGV[5] = maxMachineId
--   claimExpired  ARGV[4] = now, ARGV[5] = maxMachineId, ARGV[6] = expiredBefore
--   renew         ARGV[4] = now, ARGV[5] = machineId, ARGV[6] = version
--   release       ARGV[4] = lastTimestamp, ARGV[5] = machineId, ARGV[6] = version
-- Returns {machineId, lastTimestamp} when the operation applied, an empty array otherwise (a nil reply reaches
-- Spring as a one-element list).

local prefix = 'cosid:' .. KEYS[1] .. ':'
local idxKey = prefix .. 'itc_idx'
local revertKey = prefix .. 'revert'
local adderKey = prefix .. 'adder'
local stableKey = prefix .. 'stable'

local operation = ARGV[1]
local instanceId = ARGV[2]
local stable = ARGV[3] == '1'
local now = tonumber(ARGV[4])

local function parse(state)
    local sep = string.find(state, '|', 1, true)
    return tonumber(string.sub(state, 1, sep - 1)), tonumber(string.sub(state, sep + 1))
end

local function nextTimestamp(stored, target)
    if stored + 1 > target then
        return stored + 1
    end
    return target
end

local function take(machineId, storedTimestamp)
    local timestamp = nextTimestamp(storedTimestamp, now)
    redis.call('hset', idxKey, instanceId, string.format('%d|%d', machineId, timestamp))
    if stable then
        redis.call('sadd', stableKey, instanceId)
    else
        redis.call('srem', stableKey, instanceId)
    end
    return { machineId, timestamp }
end

if operation == 'claimSelf' then
    local state = redis.call('hget', idxKey, instanceId)
    if not state then
        return {}
    end
    local machineId, timestamp = parse(state)
    return take(machineId, timestamp)
end

if operation == 'claimReleased' then
    local maxMachineId = tonumber(ARGV[5])
    local released = redis.call('hgetall', revertKey)
    local bestId, bestTimestamp
    for i = 1, #released, 2 do
        local machineId = tonumber(released[i])
        if machineId <= maxMachineId and (bestId == nil or machineId < bestId) then
            bestId, bestTimestamp = machineId, tonumber(released[i + 1])
        end
    end
    if bestId == nil then
        return {}
    end
    redis.call('hdel', revertKey, string.format('%d', bestId))
    return take(bestId, bestTimestamp)
end

if operation == 'claimNew' then
    local maxMachineId = tonumber(ARGV[5])
    local last = redis.call('get', adderKey)
    if not last then
        -- Same start as machine_id_distribute.lua: INCR of a missing key would skip machine id 0.
        last = -1
        redis.call('set', adderKey, last)
    end
    if tonumber(last) >= maxMachineId then
        return {}
    end
    local machineId = redis.call('incr', adderKey)
    return take(machineId, now - 1)
end

if operation == 'claimExpired' then
    local maxMachineId = tonumber(ARGV[5])
    local expiredBefore = tonumber(ARGV[6])
    local entries = redis.call('hgetall', idxKey)
    local bestInstance, bestId, bestTimestamp
    for i = 1, #entries, 2 do
        local owner = entries[i]
        local machineId, timestamp = parse(entries[i + 1])
        if machineId <= maxMachineId and timestamp <= expiredBefore and redis.call('sismember', stableKey, owner) == 0
            and (bestId == nil or machineId < bestId) then
            bestInstance, bestId, bestTimestamp = owner, machineId, timestamp
        end
    end
    if bestId == nil then
        return {}
    end
    redis.call('hdel', idxKey, bestInstance)
    return take(bestId, bestTimestamp)
end

-- renew and release: only with the current version
local machineId = tonumber(ARGV[5])
local version = tonumber(ARGV[6])
local state = redis.call('hget', idxKey, instanceId)
if not state then
    return {}
end
local storedId, storedTimestamp = parse(state)
if storedId ~= machineId or storedTimestamp ~= version then
    return {}
end

if operation == 'renew' then
    return take(machineId, storedTimestamp)
end

if operation == 'release' then
    local timestamp = nextTimestamp(storedTimestamp, now)
    if stable then
        redis.call('hset', idxKey, instanceId, string.format('%d|%d', machineId, timestamp))
    else
        redis.call('hdel', idxKey, instanceId)
        redis.call('hset', revertKey, string.format('%d', machineId), string.format('%d', timestamp))
        redis.call('srem', stableKey, instanceId)
    end
    return { machineId, timestamp }
end

return redis.error_reply('Unknown operation: ' .. tostring(operation))
