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

package me.ahoo.cosid.runtime;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.StringIdGenerator;
import me.ahoo.cosid.accessor.IdFiller;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorInfo;
import me.ahoo.cosid.segment.PeriodicSegmentGenerator;

import java.util.Map;
import java.util.Optional;

/**
 * The one registry and lifecycle owner of stores, the machine slot and generators (07-runtime-and-spring.md §1).
 * Its generators are fixed at start (D17); there is no global singleton, and runtimes in one JVM do not share a slot.
 */
public final class CosIdRuntime implements AutoCloseable {
    private CosIdRuntime() {
    }

    /**
     * Opens the stores, acquires the slot and creates the generators, rolling back what was created on failure.
     *
     * @param spec    the spec
     * @param options the options
     * @return the started runtime
     * @throws me.ahoo.cosid.CosIdException if the spec is invalid (a time-based generator without a machine, a layout
     *                                      narrower than the machine bits) or a step fails
     */
    public static CosIdRuntime start(RuntimeSpec spec, RuntimeOptions options) {
        throw new UnsupportedOperationException("R3");
    }

    /**
     * The {@code long} generator of that name.
     *
     * @param name the generator name
     * @return the generator
     * @throws java.util.NoSuchElementException if there is none, or it issues no {@code long} ids
     */
    public IdGenerator generator(String name) {
        throw new UnsupportedOperationException("R3");
    }

    /**
     * The generator of that name with its codec.
     *
     * @param name the generator name
     * @return the generator
     * @throws java.util.NoSuchElementException if there is none, or it has no {@code long} ids
     */
    public StringIdGenerator stringGenerator(String name) {
        throw new UnsupportedOperationException("R3");
    }

    /**
     * The periodic segment generator of that name.
     *
     * @param name the generator name
     * @return the generator
     * @throws java.util.NoSuchElementException if there is none
     */
    public PeriodicSegmentGenerator periodic(String name) {
        throw new UnsupportedOperationException("R3");
    }

    /**
     * The generator of that name as {@code type}, such as {@code TimeBasedGenerator} or {@code IntIdGenerator}.
     *
     * @param name the generator name
     * @param type the wanted type
     * @param <T>  the wanted type
     * @return the generator
     * @throws java.util.NoSuchElementException if there is none of that type
     */
    public <T> T get(String name, Class<T> type) {
        throw new UnsupportedOperationException("R3");
    }

    public Map<String, GeneratorInfo> describe() {
        throw new UnsupportedOperationException("R3");
    }

    /**
     * The slot lease.
     *
     * @return the lease, or empty without a leased machine slot
     */
    public Optional<SlotLease> slot() {
        throw new UnsupportedOperationException("R3");
    }

    public IdFiller filler() {
        throw new UnsupportedOperationException("R3");
    }

    /**
     * Closes in reverse dependency order: generators (terminal state), the lease keeper (release), stores, the own
     * executor.
     */
    @Override
    public void close() {
        throw new UnsupportedOperationException("R3");
    }
}
