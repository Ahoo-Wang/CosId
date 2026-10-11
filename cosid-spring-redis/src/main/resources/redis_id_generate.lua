-- Arguments and the result stay strings: Lua numbers are doubles, so tonumber / an integer reply would round
-- values above 2^53 and hand out a segment again.
local adderKey = KEYS[1];
local offset = ARGV[1];
local step = ARGV[2];

redis.call('setnx', adderKey, offset);
redis.call('incrby', adderKey, step);
return redis.call('get', adderKey);
