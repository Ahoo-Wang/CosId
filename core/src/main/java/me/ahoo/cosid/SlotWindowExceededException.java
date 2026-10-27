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
 * The timestamp to issue lies beyond the ceiling of the slot window (05-machine-lease.md §3).
 */
public class SlotWindowExceededException extends CosIdException {
    private final String generator;
    private final long timestampMillis;
    private final long ceiling;

    public SlotWindowExceededException(String generator, long timestampMillis, long ceiling) {
        super("Timestamp " + timestampMillis + " exceeds the slot window ceiling " + ceiling + " in generator [" + generator + "]");
        this.generator = generator;
        this.timestampMillis = timestampMillis;
        this.ceiling = ceiling;
    }

    public String generator() {
        return generator;
    }

    public long timestampMillis() {
        return timestampMillis;
    }

    public long ceiling() {
        return ceiling;
    }
}
