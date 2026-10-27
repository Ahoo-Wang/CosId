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

package me.ahoo.cosid.store.tck;

import me.ahoo.cosid.time.TimeSource;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A clock that moves only when told to, in either direction: the store clock of an {@link InMemoryStore} or the
 * client clock of a generator under test. Thread-safe.
 */
public final class ManualClock implements TimeSource {
    private final AtomicLong millis;

    /**
     * A clock at {@code startMillis}.
     *
     * @param startMillis milliseconds since the Unix epoch
     */
    public ManualClock(long startMillis) {
        this.millis = new AtomicLong(startMillis);
    }

    /**
     * A clock at the current system time.
     */
    public ManualClock() {
        this(System.currentTimeMillis());
    }

    @Override
    public long currentMillis() {
        return millis.get();
    }

    /**
     * Sets the time, forwards or backwards.
     *
     * @param newMillis milliseconds since the Unix epoch
     */
    public void set(long newMillis) {
        millis.set(newMillis);
    }

    /**
     * Moves the time by {@code amount}; a negative amount moves it back.
     *
     * @param amount how far to move
     * @return the new time
     */
    public long advance(Duration amount) {
        return millis.addAndGet(amount.toMillis());
    }
}
