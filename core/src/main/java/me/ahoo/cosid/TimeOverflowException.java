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
 * The time of a time-based generator no longer fits the time bits of its layout (03-core-model.md §4); replaces 4.x
 * {@code TimestampOverflowException}. The generator issues no more ids: choose a layout with more time bits or a later
 * epoch.
 */
public class TimeOverflowException extends CosIdException {
    private final String generator;
    private final int timeBits;

    /**
     * The time overflowed.
     *
     * @param generator the generator name
     * @param time      the time since the epoch, in the layout's unit
     * @param timeBits  the time bits of the layout
     */
    public TimeOverflowException(String generator, long time, int timeBits) {
        super("The time " + time + " overflows the " + timeBits + " time bits of generator [" + generator + "]");
        this.generator = generator;
        this.timeBits = timeBits;
    }

    /**
     * The generator name.
     *
     * @return the name
     */
    public String generator() {
        return generator;
    }

    /**
     * The time bits of the layout.
     *
     * @return the time bits
     */
    public int timeBits() {
        return timeBits;
    }
}
