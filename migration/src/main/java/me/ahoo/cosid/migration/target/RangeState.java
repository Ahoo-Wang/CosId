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

package me.ahoo.cosid.migration.target;

/**
 * A 5.0 range key as the target store holds it.
 *
 * @param key    the range key, {@code {namespace}/{name}[/{period}]}
 * @param next   the next value a reserve hands out
 * @param sealed whether the key is sealed
 */
public record RangeState(String key, long next, boolean sealed) {
}
