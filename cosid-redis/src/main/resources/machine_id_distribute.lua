-- itc_idx: [Type:Hash] instanceId > machineId
-- adder: [Type:String] machineId adder
-- revert: [Type:Hash] machineId lastStamp

local stateDelimiter = "|";
local namespace = KEYS[1];
local instanceId = ARGV[1];
local maxMachineId = tonumber(ARGV[2]);
local instanceIdxKey = 'cosid' .. ':' .. namespace .. ':itc_idx';
local machineIdAdderKey = 'cosid' .. ':' .. namespace .. ':adder';

local function convertStingToState(machineState)
    local splitIdx = string.find(machineState, stateDelimiter, 1);
    local machineId = string.sub(machineState, 1, splitIdx - 1);
    local timestamp = string.sub(machineState, splitIdx + 1, -1);
    return { tonumber(machineId), tonumber(timestamp) }
end

local function convertStateToString(machineId, timestamp)
    return tostring(machineId) .. stateDelimiter .. tostring(timestamp);
end

local machineState = redis.call('hget', instanceIdxKey, instanceId)
if machineState then
    local states = convertStingToState(machineState);
    return { tonumber(states[1]), tonumber(states[2]) };
end

local instanceRevertKey = 'cosid' .. ':' .. namespace .. ':revert';
local machineData = redis.call('hgetall', instanceRevertKey);
local machineId = 0;
if #machineData > 0 then
    machineId = machineData[1];
    local lastStamp = machineData[2];
    machineState = convertStateToString(machineId, lastStamp);
    redis.call('hset', instanceIdxKey, instanceId, machineState);
    redis.call('hdel', instanceRevertKey, machineId)
    return { tonumber(machineId), tonumber(lastStamp) };
end

local lastMachineId = redis.call('get', machineIdAdderKey)
if not lastMachineId then
    lastMachineId = -1;
    redis.call('set', machineIdAdderKey, machineId)
end

lastMachineId = tonumber(lastMachineId);
if lastMachineId < maxMachineId then
    if lastMachineId ~= -1 then
        machineId = redis.call('incr', machineIdAdderKey);
    end
    machineState = convertStateToString(machineId, 0);
    redis.call('hset', instanceIdxKey, instanceId, machineState);
    return { machineId, 0 };
end

return { -1, -1 };



