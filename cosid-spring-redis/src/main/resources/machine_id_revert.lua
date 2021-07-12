local stateDelimiter = "|";
local namespace = KEYS[1];
local instanceId = ARGV[1];
local lastStamp = ARGV[2];
local instanceIdxKey = 'cosid' .. ':' .. namespace .. ':itc_idx';
local instanceRevertKey = 'cosid' .. ':' .. namespace .. ':revert';

local function convertStateFromString(machineState)
    local splitIdx = string.find(machineState, stateDelimiter, 1);
    local machineId = string.sub(machineState, 1, splitIdx - 1);
    local stamp = string.sub(machineState, splitIdx + 1, -1);
    return { machineId, stamp }
end

local machineState = redis.call('hget', instanceIdxKey, instanceId)
if machineState then
    redis.call('hdel', instanceIdxKey, instanceId);
    local states = convertStateFromString(machineState);
    local machineId = states[1];
    redis.call('hset', instanceRevertKey, machineId, lastStamp);
    return 1;
end

return 0;
