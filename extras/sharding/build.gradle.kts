/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// The sharding algorithms (D7, 11-feature-parity.md §6): ModCycle, IntervalTimeline, CachedSharding and the
// convertors of 4.x, published as cosid-sharding. It depends on core alone: ShardingRange replaces Guava's Range, and
// the Snowflake convertor reads the time through Layout.
plugins {
    id("cosid.library")
}

dependencies {
    api(project(":core"))
}
