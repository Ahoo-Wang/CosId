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

-- TargetAdmin.raise on the 5.0 Redis store (06-stores.md §2.2). KEYS: cosid:{key}, cosid:{key}:sealed. ARGV: next.
-- The values stay strings, compared as non-negative decimals (length first): Lua numbers would round above 2^53.
local current = redis.call('GET', KEYS[1])
local function less(a, b)
    if #a ~= #b then
        return #a < #b
    end
    return a < b
end
if not current or less(current, ARGV[1]) then
    redis.call('SET', KEYS[1], ARGV[1])
    current = ARGV[1]
end
return {current, tostring(redis.call('EXISTS', KEYS[2]))}
