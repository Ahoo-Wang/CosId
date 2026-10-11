-- MachineLeaseStore primitives on the same keys as machine_id_distribute.lua, so 3.4 and 4.0 instances can serve one
-- namespace:
--   cosid:{namespace}:itc_idx  [Hash]   instanceId -> "machineId|legacyTimestamp"
--   cosid:{namespace}:revert   [Hash]   machineId  -> legacyTimestamp (released machine ids)
--   cosid:{namespace}:adder    [String] last machine id ever handed out (-1 when none)
--   cosid:{namespace}:stable   [Set]    instance ids of stable instances, never recycled
--   cosid:{namespace}:lease    [Hash]   machineId  -> "renewedAt|watermark|version|legacyTimestamp" (4.0 only)
-- 3.4 reads the legacy timestamp as both liveness and watermark, so 4.0 writes max(renewedAt, watermark, previous
-- legacy timestamp) there: it never decreases and never makes a lease look older than it is. A lease entry is only
-- trusted while its legacy timestamp matches the one in itc_idx or revert; otherwise a 3.4 instance wrote last, and the
-- legacy timestamp is the renewal time, the watermark and the version.
-- Integers are formatted with %d: tostring would turn large numbers into floats ("1.7e+12").
--
-- KEYS[1] = namespace (hash tag); ARGV[1] = operation, ARGV[2] = instanceId, ARGV[3] = stable ("1"/"0"), then:
--   claimSelf     ARGV[4] = now
--   claimReleased ARGV[4] = now, ARGV[5] = maxMachineId
--   claimNew      ARGV[4] = now, ARGV[5] = maxMachineId
--   claimExpired  ARGV[4] = now, ARGV[5] = maxMachineId, ARGV[6] = expiredBefore
--   renew         ARGV[4] = now, ARGV[5] = machineId, ARGV[6] = version
--   release       ARGV[4] = watermark, ARGV[5] = machineId, ARGV[6] = version
-- Returns {machineId, watermark, renewedAt, version} when the operation applied, an empty array otherwise (a nil reply
-- reaches Spring as a one-element list).

local prefix = 'cosid:' .. KEYS[1] .. ':'
local idxKey = prefix .. 'itc_idx'
local revertKey = prefix .. 'revert'
local adderKey = prefix .. 'adder'
local stableKey = prefix .. 'stable'
local leaseKey = prefix .. 'lease'

local operation = ARGV[1]
local instanceId = ARGV[2]
local stable = ARGV[3] == '1'
local now = tonumber(ARGV[4])

local function parseState(state)
    local sep = string.find(state, '|', 1, true)
    return tonumber(string.sub(state, 1, sep - 1)), tonumber(string.sub(state, sep + 1))
end

local function fmt(number)
    return string.format('%d', number)
end

-- The lease of a machine id whose legacy timestamp is legacy: renewedAt, watermark, version.
local function load(machineId, legacy)
    local entry = redis.call('hget', leaseKey, fmt(machineId))
    if entry then
        local values = {}
        for part in string.gmatch(entry, '[^|]+') do
            values[#values + 1] = tonumber(part)
        end
        if values[4] == legacy then
            return values[1], values[2], values[3]
        end
    end
    return legacy, legacy, legacy
end

-- Writes the lease entry and returns the legacy timestamp to store in itc_idx or revert.
local function save(machineId, renewedAt, watermark, version, previousLegacy)
    local legacy = math.max(renewedAt, watermark, previousLegacy)
    redis.call('hset', leaseKey, fmt(machineId),
        string.format('%d|%d|%d|%d', renewedAt, watermark, version, legacy))
    return legacy
end

local function markStable()
    if stable then
        redis.call('sadd', stableKey, instanceId)
    else
        redis.call('srem', stableKey, instanceId)
    end
end

-- Hand an existing machine id to this instance: the watermark covers the previous owner's last renewal.
local function take(machineId, legacy)
    local renewedAt, watermark, version = load(machineId, legacy)
    watermark = math.max(watermark, renewedAt)
    version = version + 1
    local newLegacy = save(machineId, now, watermark, version, legacy)
    redis.call('hset', idxKey, instanceId, fmt(machineId) .. '|' .. fmt(newLegacy))
    markStable()
    return { machineId, watermark, now, version }
end

if operation == 'claimSelf' then
    local state = redis.call('hget', idxKey, instanceId)
    if not state then
        return {}
    end
    local machineId, legacy = parseState(state)
    return take(machineId, legacy)
end

if operation == 'claimReleased' then
    local maxMachineId = tonumber(ARGV[5])
    local released = redis.call('hgetall', revertKey)
    local bestId, bestLegacy
    for i = 1, #released, 2 do
        local machineId = tonumber(released[i])
        if machineId <= maxMachineId and (bestId == nil or machineId < bestId) then
            bestId, bestLegacy = machineId, tonumber(released[i + 1])
        end
    end
    if bestId == nil then
        return {}
    end
    redis.call('hdel', revertKey, fmt(bestId))
    return take(bestId, bestLegacy)
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
    local legacy = save(machineId, now, 0, 1, now)
    redis.call('hset', idxKey, instanceId, fmt(machineId) .. '|' .. fmt(legacy))
    markStable()
    return { machineId, 0, now, 1 }
end

if operation == 'claimExpired' then
    local maxMachineId = tonumber(ARGV[5])
    local expiredBefore = tonumber(ARGV[6])
    local entries = redis.call('hgetall', idxKey)
    local bestInstance, bestId, bestLegacy
    for i = 1, #entries, 2 do
        local owner = entries[i]
        local machineId, legacy = parseState(entries[i + 1])
        if machineId <= maxMachineId and (bestId == nil or machineId < bestId)
            and redis.call('sismember', stableKey, owner) == 0 then
            local renewedAt = load(machineId, legacy)
            if renewedAt <= expiredBefore then
                bestInstance, bestId, bestLegacy = owner, machineId, legacy
            end
        end
    end
    if bestId == nil then
        return {}
    end
    redis.call('hdel', idxKey, bestInstance)
    return take(bestId, bestLegacy)
end

-- renew and release: only with the current version
local machineId = tonumber(ARGV[5])
local version = tonumber(ARGV[6])
local state = redis.call('hget', idxKey, instanceId)
if not state then
    return {}
end
local storedId, legacy = parseState(state)
if storedId ~= machineId then
    return {}
end
local renewedAt, watermark, storedVersion = load(machineId, legacy)
if storedVersion ~= version then
    return {}
end

if operation == 'renew' then
    local newLegacy = save(machineId, now, watermark, version + 1, legacy)
    redis.call('hset', idxKey, instanceId, fmt(machineId) .. '|' .. fmt(newLegacy))
    return { machineId, watermark, now, version + 1 }
end

if operation == 'release' then
    watermark = math.max(watermark, now)
    local newLegacy = save(machineId, renewedAt, watermark, version + 1, legacy)
    if stable then
        redis.call('hset', idxKey, instanceId, fmt(machineId) .. '|' .. fmt(newLegacy))
    else
        redis.call('hdel', idxKey, instanceId)
        redis.call('hset', revertKey, fmt(machineId), fmt(newLegacy))
        redis.call('srem', stableKey, instanceId)
    end
    return { machineId, watermark, renewedAt, version + 1 }
end

return redis.error_reply('Unknown operation: ' .. tostring(operation))
