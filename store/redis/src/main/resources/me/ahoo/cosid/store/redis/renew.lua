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

-- SlotLeaseStore.renew (05-machine-lease.md §2.2). KEYS: cosid:{ns}:slot, :free, :live.
-- ARGV: slot, incarnation, fence, ttlMillis, toleranceMillis. Idempotent: the fence stays.
local slotKey, freeKey, liveKey = KEYS[1], KEYS[2], KEYS[3]
local slot = ARGV[1]
local raw = redis.call('HGET', slotKey, slot)
if not raw then
    return {'lost'}
end
local row = decode(raw)
if row.inc == '' or row.inc ~= ARGV[2] or row.fence ~= tonumber(ARGV[3]) then
    return {'lost'}
end
local now = nowMillis()
row.expiresAt = math.max(row.expiresAt, now + tonumber(ARGV[4]))
row.ceiling = math.max(row.ceiling, row.expiresAt + tonumber(ARGV[5]))
row.nextFloor = row.ceiling
redis.call('HSET', slotKey, slot, encode(row))
-- An expired lease the sweep moved to free is live again: nobody took it over, the fence still matches.
redis.call('ZREM', freeKey, slot)
redis.call('ZADD', liveKey, num(row.expiresAt), slot)
return {'renewed', num(row.fence), num(row.expiresAt), num(row.floor), num(row.ceiling), num(now)}
