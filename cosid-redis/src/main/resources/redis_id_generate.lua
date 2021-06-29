local namespace = KEYS[1];
local name = ARGV[1];
local offset = tonumber(ARGV[2]);
local step = tonumber(ARGV[3]);

local adderKey = 'cosid' .. ':' .. namespace .. ':' .. name .. ':adder';
redis.call('setnx', adderKey, offset);
return redis.call("incrby", adderKey, step);
