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

package me.ahoo.cosid.internal;

import me.ahoo.cosid.GeneratorDisabledException;
import me.ahoo.cosid.GroupedId;
import me.ahoo.cosid.GroupedIdGenerator;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.IdGenerators;
import me.ahoo.cosid.PartsIdGenerator;
import me.ahoo.cosid.StringIdGenerator;
import me.ahoo.cosid.codec.GroupedCodec;
import me.ahoo.cosid.codec.IdCodec;
import me.ahoo.cosid.codec.PartsCodec;
import me.ahoo.cosid.lease.SlotBinding;
import me.ahoo.cosid.lease.SlotHandle;
import me.ahoo.cosid.observe.GeneratorInfo;
import me.ahoo.cosid.segment.PeriodicSegmentGenerator;
import me.ahoo.cosid.segment.SegmentGenerator;
import me.ahoo.cosid.time.IdParts;
import me.ahoo.cosid.time.Layout;
import me.ahoo.cosid.time.TimeBasedGenerator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * A generator of a runtime with the views its lookups hand out: its {@code long}, {@code int}, parts or grouped ids, its
 * codec, its text ids and the generator itself. With a counter ({@code RuntimeOptions.countGenerated}) every view but
 * the concrete generator counts the ids it issues; without one the views are the generator itself, so the hot path is
 * unchanged.
 */
public final class GeneratorEntry {
    public static final String SNOWFLAKE = "snowflake";
    public static final String COSID80 = "cosid80";
    public static final String SEGMENT = "segment";
    public static final String PERIODIC_SEGMENT = "periodic-segment";

    private final String name;
    private final String strategy;
    private final String store;
    private final AutoCloseable generator;
    private final IdGenerator ids;
    private final StringIdGenerator strings;
    private final Supplier<String> text;
    private final List<Object> views;
    private final Supplier<Map<String, Object>> details;
    private final LongAdder counter;

    private GeneratorEntry(String name, String strategy, String store, AutoCloseable generator, IdGenerator ids, IdCodec codec,
                           Supplier<String> text, List<Object> extraViews, Supplier<Map<String, Object>> details, LongAdder counter) {
        this.name = Objects.requireNonNull(name, "name");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
        this.store = Objects.requireNonNull(store, "store");
        this.generator = generator;
        this.ids = ids;
        this.strings = ids == null ? null : new StringIdGenerator(ids, codec);
        this.text = text;
        this.counter = counter;
        this.details = counter == null ? details : () -> {
            Map<String, Object> all = new LinkedHashMap<>(details.get());
            all.put("generated", counter.sum());
            return all;
        };
        List<Object> all = new ArrayList<>();
        if (generator != null) {
            if (ids != null) {
                all.addAll(List.of(ids, strings, IdGenerators.toInt(ids), codec));
            }
            all.addAll(extraViews);
            all.add(generator);
        }
        this.views = List.copyOf(all);
    }

    private static IdGenerator counted(IdGenerator ids, LongAdder counter) {
        if (counter == null) {
            return ids;
        }
        return () -> {
            long id = ids.generate();
            counter.increment();
            return id;
        };
    }

    private static PartsIdGenerator counted(PartsIdGenerator parts, LongAdder counter) {
        if (counter == null) {
            return parts;
        }
        return () -> {
            IdParts id = parts.nextParts();
            counter.increment();
            return id;
        };
    }

    private static GroupedIdGenerator counted(GroupedIdGenerator grouped, LongAdder counter) {
        if (counter == null) {
            return grouped;
        }
        return () -> {
            GroupedId id = grouped.generate();
            counter.increment();
            return id;
        };
    }

    /**
     * A Snowflake generator.
     *
     * @param generator the generator
     * @param codec     its codec
     * @param slot      the slot it issues on
     * @param counter   counts the ids issued; null for none
     * @return the entry
     */
    public static GeneratorEntry snowflake(TimeBasedGenerator generator, IdCodec codec, SlotHandle slot, LongAdder counter) {
        Objects.requireNonNull(codec, "codec");
        IdGenerator ids = counted((IdGenerator) generator, counter);
        return new GeneratorEntry(generator.name(), SNOWFLAKE, "", generator, ids, codec,
            () -> codec.encode(ids.generate()), List.of(counted((PartsIdGenerator) generator, counter)),
            () -> timeBasedDetails(generator, slot), counter);
    }

    /**
     * An 80-bit CosId generator: parts and text ids only.
     *
     * @param generator the generator
     * @param codec     its parts codec
     * @param slot      the slot it issues on
     * @param counter   counts the ids issued; null for none
     * @return the entry
     */
    public static GeneratorEntry cosId80(TimeBasedGenerator generator, PartsCodec codec, SlotHandle slot, LongAdder counter) {
        Objects.requireNonNull(codec, "codec");
        PartsIdGenerator parts = counted((PartsIdGenerator) generator, counter);
        return new GeneratorEntry(generator.name(), COSID80, "", generator, null, null,
            () -> codec.encode(parts.nextParts()), List.of(parts, codec), () -> timeBasedDetails(generator, slot), counter);
    }

    /**
     * A segment generator.
     *
     * @param generator the generator
     * @param codec     its codec
     * @param store     the store name
     * @param counter   counts the ids issued; null for none
     * @return the entry
     */
    public static GeneratorEntry segment(SegmentGenerator generator, IdCodec codec, String store, LongAdder counter) {
        Objects.requireNonNull(codec, "codec");
        IdGenerator ids = counted(generator, counter);
        return new GeneratorEntry(generator.name(), SEGMENT, store, generator, ids, codec,
            () -> codec.encode(ids.generate()), List.of(), () -> segmentDetails(generator), counter);
    }

    /**
     * A periodic segment generator: grouped ids only.
     *
     * @param generator the generator
     * @param codec     its grouped codec
     * @param store     the store name
     * @param details   what describe() tells besides the current period
     * @param counter   counts the ids issued; null for none
     * @return the entry
     */
    public static GeneratorEntry periodic(PeriodicSegmentGenerator generator, GroupedCodec codec, String store, Map<String, Object> details,
                                          LongAdder counter) {
        Objects.requireNonNull(codec, "codec");
        Map<String, Object> fixed = Map.copyOf(details);
        GroupedIdGenerator grouped = counted(generator, counter);
        return new GeneratorEntry(generator.name(), PERIODIC_SEGMENT, store, generator, null, null,
            () -> codec.encode(grouped.generate()), List.of(grouped, codec), () -> {
                Map<String, Object> all = new LinkedHashMap<>(fixed);
                all.put("period", generator.currentPeriod());
                return all;
            }, counter);
    }

    /**
     * A disabled generator (D24): every lookup throws {@link GeneratorDisabledException}.
     *
     * @param name     the generator name
     * @param strategy the strategy it would have
     * @param store    the store it would use, empty for none
     * @return the entry
     */
    public static GeneratorEntry disabled(String name, String strategy, String store) {
        return new GeneratorEntry(name, strategy, store, null, null, null, null, List.of(), () -> Map.of("disabled", true), null);
    }

    /**
     * The ids issued through the views so far.
     *
     * @return the count; empty when not counting or disabled
     */
    public OptionalLong generated() {
        return counter == null || generator == null ? OptionalLong.empty() : OptionalLong.of(counter.sum());
    }

    public String name() {
        return name;
    }

    public String strategy() {
        return strategy;
    }

    public boolean isDisabled() {
        return generator == null;
    }

    /**
     * The time-based generator, to close it first and read its last issued time.
     *
     * @return the generator, or empty for other strategies
     */
    public Optional<TimeBasedGenerator> timeBased() {
        return generator instanceof TimeBasedGenerator timeBased ? Optional.of(timeBased) : Optional.empty();
    }

    private void requireEnabled() {
        if (generator == null) {
            throw new GeneratorDisabledException(name);
        }
    }

    /**
     * The {@code long} ids.
     *
     * @return the generator
     * @throws GeneratorDisabledException if the generator is disabled
     * @throws NoSuchElementException     if it issues no {@code long} ids
     */
    public IdGenerator ids() {
        requireEnabled();
        if (ids == null) {
            throw new NoSuchElementException("Generator [" + name + "] (" + strategy + ") issues no long ids");
        }
        return ids;
    }

    /**
     * The {@code long} ids with the codec.
     *
     * @return the generator
     * @throws GeneratorDisabledException if the generator is disabled
     * @throws NoSuchElementException     if it issues no {@code long} ids
     */
    public StringIdGenerator strings() {
        ids();
        return strings;
    }

    /**
     * The text ids of any strategy: the codec of the {@code long} id, the parts codec or the grouped codec.
     *
     * @return the text ids
     * @throws GeneratorDisabledException if the generator is disabled
     */
    public Supplier<String> text() {
        requireEnabled();
        return text;
    }

    /**
     * The first view that is a {@code type}.
     *
     * @param type the wanted type
     * @param <T>  the wanted type
     * @return the view
     * @throws GeneratorDisabledException if the generator is disabled
     * @throws NoSuchElementException     if no view is a {@code type}
     */
    public <T> T as(Class<T> type) {
        Objects.requireNonNull(type, "type");
        requireEnabled();
        for (Object view : views) {
            if (type.isInstance(view)) {
                return type.cast(view);
            }
        }
        throw new NoSuchElementException("Generator [" + name + "] (" + strategy + ") is not a " + type.getName());
    }

    /**
     * What describe() tells about this generator.
     *
     * @return the info
     */
    public GeneratorInfo info() {
        return new GeneratorInfo(name, strategy, store, details.get());
    }

    /**
     * Closes the generator; a disabled one has nothing to close.
     *
     * @throws Exception if the generator fails to close
     */
    public void close() throws Exception {
        if (generator != null) {
            generator.close();
        }
    }

    private static Map<String, Object> timeBasedDetails(TimeBasedGenerator generator, SlotHandle slot) {
        Layout layout = generator.layout();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("epoch", layout.epoch().toString());
        details.put("unit", layout.unit().toString());
        details.put("timeBits", layout.timeBits());
        details.put("nodeBits", layout.nodeBits());
        details.put("seqBits", layout.seqBits());
        details.put("lastIssuedMillis", generator.lastIssuedMillis());
        SlotBinding binding = slot.current();
        details.put("slot", binding.slot());
        details.put("floor", binding.floor());
        details.put("ceiling", binding.ceiling());
        details.put("slotValid", binding.isValid());
        return details;
    }

    private static Map<String, Object> segmentDetails(SegmentGenerator generator) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("key", generator.key());
        details.put("step", generator.policy().step());
        details.put("maxBatch", generator.policy().maxBatch());
        details.put("lookahead", generator.policy().lookahead().toString());
        details.put("reserveTimeout", generator.policy().reserveTimeout().toString());
        details.put("batch", generator.batch());
        details.put("held", Math.max(0, generator.held()));
        return details;
    }
}
