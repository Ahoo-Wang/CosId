-- itc_idx: [Type:Hash] instanceId > machineId
-- adder: [Type:String] machineId adder
-- revert: [Type:Hash] machineId lastStamp

local stateDelimiter = "|";
local keyPrefix = 'cosid' .. ':';
local namespace = KEYS[1];
local instanceId = ARGV[1];
local maxMachineId = tonumber(ARGV[2]);
local currentStamp = tonumber(ARGV[3]);
local safeGuardAt = tonumber(ARGV[4]);

local instanceIdxKey = keyPrefix .. namespace .. ':itc_idx';
local machineIdAdderKey = keyPrefix .. namespace .. ':adder';
local instanceRevertKey = keyPrefix .. namespace .. ':revert';

local function convertStingToState(machineState)
    local splitIdx = string.find(machineState, stateDelimiter, 1);
    local machineId = string.sub(machineState, 1, splitIdx - 1);
    local timestamp = string.sub(machineState, splitIdx + 1, -1);
    return { tonumber(machineId), tonumber(timestamp) }
end

local function convertStateToString(machineId, timestamp)
    return tostring(machineId) .. stateDelimiter .. tostring(timestamp);
end

local function setState(machineId, lastStamp)
    local machineState = convertStateToString(machineId, lastStamp);
    redis.call('hset', instanceIdxKey, instanceId, machineState);
end

local function moveRevertToCurrent(machineId, lastStamp)
    if lastStamp < currentStamp then
        lastStamp = currentStamp;
    end
    setState(machineId, lastStamp);
    redis.call('hdel', instanceRevertKey, machineId)
    return { machineId, lastStamp }
end

local function moveRecyclableToCurrent(recyclableInstanceId, machineId)
    redis.call('hdel', instanceIdxKey, recyclableInstanceId);
    setState(machineId, currentStamp);
    return { machineId, currentStamp }
end

--DistributeBySelf
local machineState = redis.call('hget', instanceIdxKey, instanceId)
if machineState then
    local states = convertStingToState(machineState);
    local machineId = states[1];
    setState(machineId, currentStamp);
    return { machineId, currentStamp }
end

--DistributeByRevert
local machineData = redis.call('hgetall', instanceRevertKey);
if #machineData > 0 then
    local machineId = tonumber(machineData[1]);
    local lastStamp = tonumber(machineData[2]);
    return moveRevertToCurrent(machineId, lastStamp)
end

--DistributeByAdder
local lastMachineId = redis.call('get', machineIdAdderKey)
if not lastMachineId then
    lastMachineId = -1;
    redis.call('set', machineIdAdderKey, lastMachineId)
end

lastMachineId = tonumber(lastMachineId);
--Not Overflow
if lastMachineId < maxMachineId then
    local machineId = redis.call('incr', machineIdAdderKey);
    setState(machineId, currentStamp);
    return { machineId, currentStamp };
end

--For Each MachineIdSlot
local instanceData = redis.call('hgetall', instanceIdxKey);

for idx, val in ipairs(instanceData) do
    if idx % 2 == 1 then
        local eachInstanceId = val;
        local eachMachineStateStr = instanceData[idx + 1];
        local eachMachineState = convertStingToState(eachMachineStateStr);
        local eachMachineId = eachMachineState[1];
        local eachLastStamp = eachMachineState[2];
        -- Recyclable
        if eachLastStamp <= safeGuardAt then
            return moveRecyclableToCurrent(eachInstanceId, eachMachineId);
        end
    end
end

return { -1, -1 };



