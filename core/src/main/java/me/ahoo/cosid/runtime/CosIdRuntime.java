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
import me.ahoo.cosid.internal.AnnotatedIdFiller;
import me.ahoo.cosid.internal.GeneratorEntry;
import me.ahoo.cosid.internal.RuntimeParts;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.observe.GeneratorInfo;
import me.ahoo.cosid.segment.PeriodicSegmentGenerator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The one registry and lifecycle owner of stores, the machine slot and generators (07-runtime-and-spring.md §1).
 * Its generators are fixed at start (D17); there is no global singleton, and runtimes in one JVM do not share a slot.
 *
 * <p>Lookups of a disabled generator (D24, {@link GeneratorSpec.Disabled}) throw
 * {@link me.ahoo.cosid.GeneratorDisabledException}.
 */
public final class CosIdRuntime implements AutoCloseable {
    private final RuntimeParts parts;
    private final Map<String, GeneratorEntry> generators;
    private final IdFiller filler;

    private CosIdRuntime(RuntimeParts parts) {
        this.parts = parts;
        this.generators = parts.generators();
        this.filler = new AnnotatedIdFiller(this::entry);
    }

    /**
     * Checks the whole spec, then opens the stores, acquires the slot and creates the generators, rolling back what was
     * created on failure. The machine slot is acquired only when a time-based generator is enabled, and only the stores
     * that the enabled generators and the slot use are opened.
     *
     * @param spec    the spec
     * @param options the options
     * @return the started runtime
     * @throws me.ahoo.cosid.CosIdException if the spec is invalid (a time-based generator without a machine, a layout
     *                                      narrower than the machine bits, an unknown store, a store timeout above the
     *                                      reserve timeout, ...) or a step fails
     */
    public static CosIdRuntime start(RuntimeSpec spec, RuntimeOptions options) {
        return start(spec, options, RuntimeParts::ownExecutor);
    }

    static CosIdRuntime start(RuntimeSpec spec, RuntimeOptions options, Function<String, ExecutorService> ownExecutors) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(options, "options");
        return new CosIdRuntime(RuntimeParts.assemble(spec, options, ownExecutors));
    }

    public String namespace() {
        return parts.namespace();
    }

    private GeneratorEntry entry(String name) {
        Objects.requireNonNull(name, "name");
        GeneratorEntry entry = generators.get(name);
        if (entry == null) {
            throw new NoSuchElementException("No generator [" + name + "] in namespace [" + parts.namespace() + "]");
        }
        return entry;
    }

    /**
     * The {@code long} generator of that name.
     *
     * @param name the generator name
     * @return the generator
     * @throws java.util.NoSuchElementException          if there is none, or it issues no {@code long} ids
     * @throws me.ahoo.cosid.GeneratorDisabledException if it is disabled
     */
    public IdGenerator generator(String name) {
        return entry(name).ids();
    }

    /**
     * The generator of that name with its codec.
     *
     * @param name the generator name
     * @return the generator
     * @throws java.util.NoSuchElementException          if there is none, or it has no {@code long} ids
     * @throws me.ahoo.cosid.GeneratorDisabledException if it is disabled
     */
    public StringIdGenerator stringGenerator(String name) {
        return entry(name).strings();
    }

    /**
     * The periodic segment generator of that name.
     *
     * @param name the generator name
     * @return the generator
     * @throws java.util.NoSuchElementException          if there is none
     * @throws me.ahoo.cosid.GeneratorDisabledException if it is disabled
     */
    public PeriodicSegmentGenerator periodic(String name) {
        return entry(name).as(PeriodicSegmentGenerator.class);
    }

    /**
     * The ids the generator of that name issued through the views of this runtime: its {@code generator},
     * {@code stringGenerator}, {@code get} by an interface type, {@code periodic(name)} excluded, and the
     * {@link #filler()}.
     *
     * @param name the generator name
     * @return the count; empty unless {@link RuntimeOptions#countGenerated()} is on, or when it is disabled
     * @throws java.util.NoSuchElementException if there is none
     */
    public OptionalLong generated(String name) {
        return entry(name).generated();
    }

    /**
     * The generator of that name as {@code type}: an {@code IdGenerator}, {@code PartsIdGenerator} (time-based) or
     * {@code GroupedIdGenerator} (periodic segment), the concrete generator ({@code TimeBasedGenerator},
     * {@code SegmentGenerator}, {@code PeriodicSegmentGenerator}; never counted), or for {@code long} ids a
     * {@code StringIdGenerator}, an {@code IntIdGenerator} (range checked) or its {@code IdCodec}; for the 80-bit CosId
     * its {@code PartsCodec}; for a periodic segment its {@code GroupedCodec}.
     *
     * @param name the generator name
     * @param type the wanted type
     * @param <T>  the wanted type
     * @return the generator
     * @throws java.util.NoSuchElementException          if there is none of that type
     * @throws me.ahoo.cosid.GeneratorDisabledException if it is disabled
     */
    public <T> T get(String name, Class<T> type) {
        return entry(name).as(type);
    }

    /**
     * What each generator is: its layout, slot and window, its ready values and batch size, and the like.
     *
     * @return the generators by name, in name order; a disabled one tells {@code disabled = true}
     */
    public Map<String, GeneratorInfo> describe() {
        Map<String, GeneratorInfo> infos = new LinkedHashMap<>();
        for (GeneratorEntry entry : generators.values()) {
            infos.put(entry.name(), entry.info());
        }
        return Collections.unmodifiableMap(infos);
    }

    /**
     * The slot lease of the default machine.
     *
     * @return the lease, or empty without a leased default machine
     */
    public Optional<SlotLease> slot() {
        return parts.lease();
    }

    /**
     * The slot lease of a machine (D37).
     *
     * @param machine the machine name; {@value RuntimeSpec#DEFAULT_MACHINE} for the default one
     * @return the lease, or empty when that machine is static or did not start (no enabled generator chose it)
     */
    public Optional<SlotLease> slot(String machine) {
        return parts.lease(Objects.requireNonNull(machine, "machine"));
    }

    /**
     * The machines that started: those an enabled time-based generator chose (D37).
     *
     * @return the machine names in name order, {@value RuntimeSpec#DEFAULT_MACHINE} included when it started
     */
    public Set<String> machines() {
        return Collections.unmodifiableSet(new TreeSet<>(parts.machines().keySet()));
    }

    /**
     * The text ids of the generator of that name, whatever its strategy (R1-int-01): the codec of its {@code long}
     * ids, its parts codec (cosid80) or its grouped codec (periodic segment).
     *
     * @param name the generator name
     * @return the text ids
     * @throws java.util.NoSuchElementException          if there is none
     * @throws me.ahoo.cosid.GeneratorDisabledException if it is disabled
     */
    public Supplier<String> text(String name) {
        return entry(name).text();
    }

    /**
     * Fills {@link me.ahoo.cosid.accessor.CosId} fields from this runtime's generators.
     *
     * @return the filler
     */
    public IdFiller filler() {
        return filler;
    }

    /**
     * Closes in reverse dependency order: generators (terminal state, time-based ones first), the slot (released with
     * the largest timestamp they issued), stores, the own executor. Closing again does nothing.
     *
     * @throws me.ahoo.cosid.CosIdException if a step failed; every step still ran
     */
    @Override
    public void close() {
        parts.close();
    }
}
