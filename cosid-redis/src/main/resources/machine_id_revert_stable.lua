local stateDelimiter = "|";
local namespace = KEYS[1];
local instanceId = ARGV[1];
local lastStamp = ARGV[2];

local instanceIdxKey = 'cosid' .. ':' .. namespace .. ':itc_idx';

local function convertStateFromString(machineState)
    local splitIdx = string.find(machineState, stateDelimiter, 1);
    local machineId = string.sub(machineState, 1, splitIdx - 1);
    local stamp = string.sub(machineState, splitIdx + 1, -1);
    return { machineId, stamp }
end

local function convertToStringState(machineId, lastStamp)
    return tostring(machineId) .. stateDelimiter .. tostring(lastStamp);
end

local machineState = redis.call('hget', instanceIdxKey, instanceId)
if machineState then
    local states = convertStateFromString(machineState)
    local machineId = states[1];
    machineState = convertToStringState(machineId, lastStamp);
    redis.call('hset', instanceIdxKey, instanceId, machineState);
    return 1;
end

return 0;
