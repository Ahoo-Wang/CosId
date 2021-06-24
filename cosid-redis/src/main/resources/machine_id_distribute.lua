-- itc_idx: [Type:Hash] instanceId > machineId
-- adder: [Type:String] machineId adder
-- revert: [Type:Hash] machineId lastStamp
local namespace = KEYS[1];
local instanceId = ARGV[1];
local maxMachineId = tonumber(ARGV[2]);
local instanceIdxKey = 'cosid' .. ':' .. namespace .. ':itc_idx';
local machineIdAdderKey = 'cosid' .. ':' .. namespace .. ':adder';

local machineId = redis.call('hget', instanceIdxKey, instanceId)
if machineId then
    return { tonumber(machineId) };
end

local instanceRevertKey = 'cosid' .. ':' .. namespace .. ':revert';
machineId = redis.call('hrandfield', instanceRevertKey);
if machineId then
    redis.call('hset', instanceIdxKey, instanceId, machineId);
    local lastStamp = redis.call('hget', instanceRevertKey, machineId)
    redis.call('hdel', instanceRevertKey, machineId)
    return { tonumber(machineId), tonumber(lastStamp) };
end

local lastMachineId = redis.call('get', machineIdAdderKey)
if not lastMachineId then
    lastMachineId = -1;
    machineId = 0;
    redis.call('set', machineIdAdderKey, machineId)
end

lastMachineId = tonumber(lastMachineId);
if lastMachineId < maxMachineId then
    if lastMachineId ~= -1 then
        machineId = redis.call('incr', machineIdAdderKey);
    end
    redis.call('hset', instanceIdxKey, instanceId, machineId);
    return { machineId };
end

return { -1 };



