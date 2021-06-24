local namespace = KEYS[1];
local instanceId = ARGV[1];
local lastStamp = ARGV[2];
local instanceIdxKey = 'cosid' .. ':' .. namespace .. ':itc_idx';
local instanceRevertKey = 'cosid' .. ':' .. namespace .. ':revert';

local machineId = redis.call('hget', instanceIdxKey, instanceId)
if machineId then
    redis.call('hdel', instanceIdxKey, instanceId)
    redis.call('hset', instanceRevertKey, machineId, lastStamp);
    return 1;
end

return 0;
