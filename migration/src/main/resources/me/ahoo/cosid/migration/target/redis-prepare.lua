-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--      http://www.apache.org/licenses/LICENSE-2.0
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- TargetAdmin.prepare on the 5.0 Redis store (09 §4.2), after store/redis slot-row.lua (decode, encode, nowMillis).
-- KEYS: cosid:{ns}:meta, :slot, :free, :live. ARGV: limit, toleranceMillis, nextFloor, slotOffset.
-- Keeps the invariant of acquire.lua: every slot below the limit is in exactly one of free and live.
-- A meta of 5.0.0-alpha.1 has no slotOffset: offset 0, as acquire.lua reads it.
local meta = redis.call('HMGET', KEYS[1], 'limit', 'tolerance', 'slotOffset')
if not meta[1] then
    redis.call('HSET', KEYS[1], 'limit', ARGV[1], 'tolerance', ARGV[2], 'slotOffset', ARGV[4])
elseif meta[1] ~= ARGV[1] or meta[2] ~= ARGV[2] or (meta[3] or '0') ~= ARGV[4] then
    return {'mismatch', meta[1], meta[2], meta[3] or '0'}
end
local live = redis.call('ZCOUNT', KEYS[4], num(nowMillis()), '+inf')
if live > 0 then
    return {'live', tostring(live)}
end
local limit = tonumber(ARGV[1])
local floor = tonumber(ARGV[3])
for slot = 0, limit - 1 do
    local member = tostring(slot)
    if not redis.call('ZSCORE', KEYS[4], member) then
        redis.call('ZADD', KEYS[3], 'NX', slot, member)
    end
    local raw = redis.call('HGET', KEYS[2], member)
    local row = decode(raw)
    if not raw or row.nextFloor < floor then
        row.nextFloor = math.max(row.nextFloor, floor)
        redis.call('HSET', KEYS[2], member, encode(row))
    end
end
return {'ok'}
