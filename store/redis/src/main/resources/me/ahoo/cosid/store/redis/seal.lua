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

-- RangeStore.seal (06-stores.md §2.2). KEYS: cosid:{key}:sealed. The counter is kept and never deleted (D23); the
-- marker alone stops reserve, also for a key that does not exist yet (S6).
redis.call('SET', KEYS[1], '1')
return {'sealed'}
