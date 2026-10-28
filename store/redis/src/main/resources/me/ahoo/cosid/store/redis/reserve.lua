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

-- RangeStore.reserve (06-stores.md §2.2). KEYS: cosid:{key}, cosid:{key}:sealed. ARGV: size, initialOffset.
-- Arguments and results stay strings: Lua numbers are doubles and would round values above 2^53, so the counter is
-- only touched by SETNX / INCRBY (64-bit integers in Redis) and read back with GET.
if redis.call('EXISTS', KEYS[2]) == 1 then
    return {'sealed'}
end
redis.call('SETNX', KEYS[1], ARGV[2])
local added = redis.pcall('INCRBY', KEYS[1], ARGV[1])
if type(added) == 'table' and added.err then
    if string.find(added.err, 'overflow', 1, true) then
        return {'exhausted'}
    end
    return redis.error_reply(added.err)
end
return {'reserved', redis.call('GET', KEYS[1])}
