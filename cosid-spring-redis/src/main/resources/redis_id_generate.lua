local adderKey = KEYS[1];
local offset = tonumber(ARGV[1]);
local step = tonumber(ARGV[2]);

redis.call('setnx', adderKey, offset);
return redis.call("incrby", adderKey, step);
