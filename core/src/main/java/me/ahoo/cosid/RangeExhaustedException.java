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

package me.ahoo.cosid;

/**
 * A reservation would pass {@code Long.MAX_VALUE}: ranges never wrap around (04-segment.md §1, S5).
 */
public class RangeExhaustedException extends CosIdException {
    private final String key;

    public RangeExhaustedException(String key) {
        super("Range key [" + key + "] is exhausted");
        this.key = key;
    }

    public String key() {
        return key;
    }
}
