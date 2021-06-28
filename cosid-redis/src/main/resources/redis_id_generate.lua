local namespace = KEYS[1];
local name = ARGV[1];
local step = tonumber(ARGV[2]);

local adderKey = 'cosid' .. ':' .. namespace .. ':' .. name .. ':adder';
return redis.call("incrby", adderKey, step);
