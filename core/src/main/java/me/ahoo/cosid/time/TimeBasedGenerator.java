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

package me.ahoo.cosid.time;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.PartsIdGenerator;
import me.ahoo.cosid.internal.SafeListener;
import me.ahoo.cosid.internal.TimeSequencer;
import me.ahoo.cosid.lease.SlotHandle;
import me.ahoo.cosid.observe.GeneratorListener;

import java.util.Objects;

/**
 * The time-based generator: Snowflake layouts and the 80-bit CosId alike (03-core-model.md §4). Reads the slot binding
 * once per id and checks the slot window before publishing the new state (§4.1); issuing is a short critical section and
 * never waits while holding it.
 */
public final class TimeBasedGenerator implements IdGenerator, PartsIdGenerator, AutoCloseable {
    private final String name;
    private final Layout layout;
    private final TimeSequencer sequencer;

    /**
     * A time-based generator.
     *
     * @param name       the generator name, for events and errors
     * @param layout     the id layout; its node bits must cover the slot bits
     * @param slot       the machine slot, shared by every time-based generator of a runtime (D18)
     * @param time       the time source
     * @param regression the clock regression policy
     * @param listener   the event listener; a listener that throws is logged and never fails or loses an id
     */
    public TimeBasedGenerator(String name, Layout layout, SlotHandle slot, TimeSource time,
                              ClockRegressionPolicy regression, GeneratorListener listener) {
        this.name = Objects.requireNonNull(name, "name");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.sequencer = new TimeSequencer(name, layout, Objects.requireNonNull(slot, "slot"), Objects.requireNonNull(time, "time"),
            Objects.requireNonNull(regression, "regression"), SafeListener.of(listener));
    }

    public String name() {
        return name;
    }

    /**
     * Issues the next id.
     *
     * @return the id
     * @throws IllegalStateException if the layout is wider than 63 bits, or the generator is closed
     */
    @Override
    public long generate() {
        if (layout.totalBits() > Layout.LONG_BITS) {
            throw new IllegalStateException("Generator [" + name + "] has a " + layout.totalBits() + "-bit layout: use nextParts()");
        }
        return sequencer.nextId();
    }

    @Override
    public IdParts nextParts() {
        return sequencer.nextParts();
    }

    public Layout layout() {
        return layout;
    }

    /**
     * The slot of the current binding.
     *
     * @return the slot
     */
    public int slot() {
        return sequencer.slot();
    }

    /**
     * The time of the last issued id.
     *
     * @return milliseconds since the Unix epoch, 0 when none was issued; exact once {@link #close()} returned
     */
    public long lastIssuedMillis() {
        return sequencer.lastIssuedMillis();
    }

    /**
     * Moves the sequencer to its terminal state; no id is issued once this returns.
     */
    @Override
    public void close() {
        sequencer.close();
    }
}
