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
import me.ahoo.cosid.segment.BatchPolicy;
import me.ahoo.cosid.segment.Period;
import me.ahoo.cosid.segment.PeriodicPolicy;
import me.ahoo.cosid.time.ClockRegressionPolicy;
import me.ahoo.cosid.time.Layout;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A generator of a {@link RuntimeSpec}, by strategy.
 *
 * <p>The variants are the current snapshot of a closed set of specs: a minor version may add a variant, so keep a
 * {@code default} branch in a {@code switch} over them (D40). The strategy variants are immutable and built with their
 * builders, the only way to create one, so fields can be added with a default (api-freeze.md §4).
 */
public sealed interface GeneratorSpec {
    /**
     * A time-based generator packing {@code long} ids.
     */
    final class Snowflake implements GeneratorSpec {
        private final Layout layout;
        private final ClockRegressionPolicy regression;
        private final List<CodecSpec> codec;
        private final Optional<String> machine;

        private Snowflake(Builder builder) {
            this.layout = builder.layout;
            this.regression = builder.regression;
            this.codec = builder.codec;
            this.machine = Optional.ofNullable(builder.machine);
        }

        /**
         * A builder of a snowflake generator.
         *
         * @return the builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * The layout; its node bits must cover the machine bits.
         *
         * @return the layout; default {@link Layout#SNOWFLAKE_MS}
         */
        public Layout layout() {
            return layout;
        }

        /**
         * The clock regression policy.
         *
         * @return the policy; default {@link ClockRegressionPolicy#DEFAULT}
         */
        public ClockRegressionPolicy regression() {
            return regression;
        }

        /**
         * The codec list, applied in order.
         *
         * @return the codec list; default empty, for none
         */
        public List<CodecSpec> codec() {
            return codec;
        }

        /**
         * The machine whose slot the ids carry (D37).
         *
         * @return the machine name; empty (the default) or {@value RuntimeSpec#DEFAULT_MACHINE} for the
         *     {@link RuntimeSpec#machine()}
         */
        public Optional<String> machine() {
            return machine;
        }

        /**
         * This generator on another machine.
         *
         * @param machine the machine name
         * @return the generator
         */
        public Snowflake withMachine(String machine) {
            return new Builder().layout(layout).regression(regression).codec(codec).machine(machine).build();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Snowflake that && layout.equals(that.layout) && regression.equals(that.regression) && codec.equals(that.codec)
                && machine.equals(that.machine);
        }

        @Override
        public int hashCode() {
            return Objects.hash(layout, regression, codec, machine);
        }

        @Override
        public String toString() {
            return "Snowflake[layout=" + layout + ", regression=" + regression + ", codec=" + codec + ", machine=" + machine + "]";
        }

        /**
         * Builds a {@link Snowflake}.
         */
        public static final class Builder {
            private Layout layout = Layout.SNOWFLAKE_MS;
            private ClockRegressionPolicy regression = ClockRegressionPolicy.DEFAULT;
            private List<CodecSpec> codec = List.of();
            private String machine;

            private Builder() {
            }

            /**
             * Sets the machine whose slot the ids carry (D37).
             *
             * @param machine the machine name; default the {@link RuntimeSpec#machine()}
             * @return this builder
             */
            public Builder machine(String machine) {
                this.machine = Objects.requireNonNull(machine, "machine");
                return this;
            }

            /**
             * Sets the layout; its node bits must cover the machine bits.
             *
             * @param layout the layout; default {@link Layout#SNOWFLAKE_MS}
             * @return this builder
             */
            public Builder layout(Layout layout) {
                this.layout = Objects.requireNonNull(layout, "layout");
                return this;
            }

            /**
             * Sets the clock regression policy.
             *
             * @param regression the policy; default {@link ClockRegressionPolicy#DEFAULT}
             * @return this builder
             */
            public Builder regression(ClockRegressionPolicy regression) {
                this.regression = Objects.requireNonNull(regression, "regression");
                return this;
            }

            /**
             * Sets the codec list, applied in order.
             *
             * @param codec the codec list; default empty, for none
             * @return this builder
             */
            public Builder codec(List<CodecSpec> codec) {
                this.codec = List.copyOf(Objects.requireNonNull(codec, "codec"));
                return this;
            }

            /**
             * Builds the generator.
             *
             * @return the generator
             */
            public Snowflake build() {
                return new Snowflake(this);
            }
        }
    }

    /**
     * A time-based generator wider than a {@code long} ({@link Layout#COSID_80} by default), issuing parts and text
     * only. Its layout keeps the Unix epoch and milliseconds, so the text of every CosId implementation decodes alike
     * (D36); {@code CosIdRuntime.start} rejects a layout of 63 bits or fewer (use {@link Snowflake}).
     */
    final class CosId80 implements GeneratorSpec {
        private final Layout layout;
        private final ClockRegressionPolicy regression;
        private final PartsCodecSpec codec;
        private final Optional<String> machine;

        private CosId80(Builder builder) {
            this.layout = builder.layout;
            this.regression = builder.regression;
            this.codec = builder.codec;
            this.machine = Optional.ofNullable(builder.machine);
        }

        /**
         * A builder of a cosid80 generator.
         *
         * @return the builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * The layout: wider than 63 bits, with the Unix epoch and milliseconds, {@code timeBits + seqBits} at most 63;
         * its node bits must cover the machine bits.
         *
         * @return the layout; default {@link Layout#COSID_80}
         */
        public Layout layout() {
            return layout;
        }

        /**
         * The clock regression policy.
         *
         * @return the policy; default {@link ClockRegressionPolicy#DEFAULT}
         */
        public ClockRegressionPolicy regression() {
            return regression;
        }

        /**
         * The parts codec.
         *
         * @return the codec; default radix 62
         */
        public PartsCodecSpec codec() {
            return codec;
        }

        /**
         * The machine whose slot the ids carry (D37).
         *
         * @return the machine name; empty (the default) or {@value RuntimeSpec#DEFAULT_MACHINE} for the
         *     {@link RuntimeSpec#machine()}
         */
        public Optional<String> machine() {
            return machine;
        }

        /**
         * This generator on another machine, such as a slot in a namespace shared across services.
         *
         * @param machine the machine name
         * @return the generator
         */
        public CosId80 withMachine(String machine) {
            return new Builder().layout(layout).regression(regression).codec(codec).machine(machine).build();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CosId80 that && layout.equals(that.layout) && regression.equals(that.regression) && codec.equals(that.codec)
                && machine.equals(that.machine);
        }

        @Override
        public int hashCode() {
            return Objects.hash(layout, regression, codec, machine);
        }

        @Override
        public String toString() {
            return "CosId80[layout=" + layout + ", regression=" + regression + ", codec=" + codec + ", machine=" + machine + "]";
        }

        /**
         * Builds a {@link CosId80}.
         */
        public static final class Builder {
            private Layout layout = Layout.COSID_80;
            private ClockRegressionPolicy regression = ClockRegressionPolicy.DEFAULT;
            private PartsCodecSpec codec = new PartsCodecSpec.Radix(62);
            private String machine;

            private Builder() {
            }

            /**
             * Sets the machine whose slot the ids carry (D37).
             *
             * @param machine the machine name; default the {@link RuntimeSpec#machine()}
             * @return this builder
             */
            public Builder machine(String machine) {
                this.machine = Objects.requireNonNull(machine, "machine");
                return this;
            }

            /**
             * Sets the layout: wider than 63 bits, with the Unix epoch and milliseconds (D36).
             *
             * @param layout the layout; default {@link Layout#COSID_80}
             * @return this builder
             */
            public Builder layout(Layout layout) {
                this.layout = Objects.requireNonNull(layout, "layout");
                return this;
            }

            /**
             * Sets the clock regression policy.
             *
             * @param regression the policy; default {@link ClockRegressionPolicy#DEFAULT}
             * @return this builder
             */
            public Builder regression(ClockRegressionPolicy regression) {
                this.regression = Objects.requireNonNull(regression, "regression");
                return this;
            }

            /**
             * Sets the parts codec.
             *
             * @param codec the codec; default radix 62
             * @return this builder
             */
            public Builder codec(PartsCodecSpec codec) {
                this.codec = Objects.requireNonNull(codec, "codec");
                return this;
            }

            /**
             * Builds the generator.
             *
             * @return the generator
             */
            public CosId80 build() {
                return new CosId80(this);
            }
        }
    }

    /**
     * A segment generator.
     */
    final class Segment implements GeneratorSpec {
        private final String store;
        private final BatchPolicy batch;
        private final List<CodecSpec> codec;

        private Segment(Builder builder) {
            this.store = builder.store;
            this.batch = builder.batch;
            this.codec = builder.codec;
        }

        /**
         * A builder of a segment generator.
         *
         * @param store the store name
         * @return the builder
         */
        public static Builder builder(String store) {
            return new Builder(store);
        }

        /**
         * The store name.
         *
         * @return the store name
         */
        public String store() {
            return store;
        }

        /**
         * The batch policy.
         *
         * @return the policy; default {@link BatchPolicy#DEFAULT}
         */
        public BatchPolicy batch() {
            return batch;
        }

        /**
         * The codec list, applied in order.
         *
         * @return the codec list; default empty, for none
         */
        public List<CodecSpec> codec() {
            return codec;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Segment that && store.equals(that.store) && batch.equals(that.batch) && codec.equals(that.codec);
        }

        @Override
        public int hashCode() {
            return Objects.hash(store, batch, codec);
        }

        @Override
        public String toString() {
            return "Segment[store=" + store + ", batch=" + batch + ", codec=" + codec + "]";
        }

        /**
         * Builds a {@link Segment}.
         */
        public static final class Builder {
            private final String store;
            private BatchPolicy batch = BatchPolicy.DEFAULT;
            private List<CodecSpec> codec = List.of();

            private Builder(String store) {
                this.store = Objects.requireNonNull(store, "store");
            }

            /**
             * Sets the batch policy.
             *
             * @param batch the policy; default {@link BatchPolicy#DEFAULT}
             * @return this builder
             */
            public Builder batch(BatchPolicy batch) {
                this.batch = Objects.requireNonNull(batch, "batch");
                return this;
            }

            /**
             * Sets the codec list, applied in order.
             *
             * @param codec the codec list; default empty, for none
             * @return this builder
             */
            public Builder codec(List<CodecSpec> codec) {
                this.codec = List.copyOf(Objects.requireNonNull(codec, "codec"));
                return this;
            }

            /**
             * Builds the generator.
             *
             * @return the generator
             */
            public Segment build() {
                return new Segment(this);
            }
        }
    }

    /**
     * A periodic segment generator.
     */
    final class PeriodicSegment implements GeneratorSpec {
        private final String store;
        private final Period period;
        private final ZoneId zone;
        private final BatchPolicy batch;
        private final PeriodicPolicy periodic;
        private final GroupedCodecSpec codec;

        private PeriodicSegment(Builder builder) {
            this.store = builder.store;
            this.period = builder.period;
            this.zone = builder.zone;
            this.batch = builder.batch;
            this.periodic = builder.periodic;
            this.codec = builder.codec;
        }

        /**
         * A builder of a periodic segment generator.
         *
         * @param store  the store name
         * @param period the period
         * @param zone   the time zone the periods follow
         * @return the builder
         */
        public static Builder builder(String store, Period period, ZoneId zone) {
            return new Builder(store, period, zone);
        }

        /**
         * The store name.
         *
         * @return the store name
         */
        public String store() {
            return store;
        }

        /**
         * The period.
         *
         * @return the period
         */
        public Period period() {
            return period;
        }

        /**
         * The time zone the periods follow.
         *
         * @return the zone
         */
        public ZoneId zone() {
            return zone;
        }

        /**
         * The batch policy of each period.
         *
         * @return the policy; default {@link BatchPolicy#DEFAULT}
         */
        public BatchPolicy batch() {
            return batch;
        }

        /**
         * The periodic policy.
         *
         * @return the policy; default {@link PeriodicPolicy#DEFAULT}
         */
        public PeriodicPolicy periodic() {
            return periodic;
        }

        /**
         * The grouped codec.
         *
         * @return the codec; default the period first, {@code -}, then the value
         */
        public GroupedCodecSpec codec() {
            return codec;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PeriodicSegment that && store.equals(that.store) && period.equals(that.period) && zone.equals(that.zone)
                && batch.equals(that.batch) && periodic.equals(that.periodic) && codec.equals(that.codec);
        }

        @Override
        public int hashCode() {
            return Objects.hash(store, period, zone, batch, periodic, codec);
        }

        @Override
        public String toString() {
            return "PeriodicSegment[store=" + store + ", period=" + period + ", zone=" + zone + ", batch=" + batch
                + ", periodic=" + periodic + ", codec=" + codec + "]";
        }

        /**
         * Builds a {@link PeriodicSegment}.
         */
        public static final class Builder {
            private final String store;
            private final Period period;
            private final ZoneId zone;
            private BatchPolicy batch = BatchPolicy.DEFAULT;
            private PeriodicPolicy periodic = PeriodicPolicy.DEFAULT;
            private GroupedCodecSpec codec = new GroupedCodecSpec.DatePrefix("-", List.of());

            private Builder(String store, Period period, ZoneId zone) {
                this.store = Objects.requireNonNull(store, "store");
                this.period = Objects.requireNonNull(period, "period");
                this.zone = Objects.requireNonNull(zone, "zone");
            }

            /**
             * Sets the batch policy of each period.
             *
             * @param batch the policy; default {@link BatchPolicy#DEFAULT}
             * @return this builder
             */
            public Builder batch(BatchPolicy batch) {
                this.batch = Objects.requireNonNull(batch, "batch");
                return this;
            }

            /**
             * Sets the periodic policy.
             *
             * @param periodic the policy; default {@link PeriodicPolicy#DEFAULT}
             * @return this builder
             */
            public Builder periodic(PeriodicPolicy periodic) {
                this.periodic = Objects.requireNonNull(periodic, "periodic");
                return this;
            }

            /**
             * Sets the grouped codec.
             *
             * @param codec the codec; default the period first, {@code -}, then the value
             * @return this builder
             */
            public Builder codec(GroupedCodecSpec codec) {
                this.codec = Objects.requireNonNull(codec, "codec");
                return this;
            }

            /**
             * Builds the generator.
             *
             * @return the generator
             */
            public PeriodicSegment build() {
                return new PeriodicSegment(this);
            }
        }
    }

    /**
     * A user {@code long} generator by reference, for configuration: {@code CosIdRuntime.start} resolves the reference
     * through {@link RuntimeOptions#generators()} and fails when it resolves to nothing.
     *
     * <p>The runtime does not own it (D41): it does not close, renew nor prefetch it, and it needs no store nor machine
     * slot. It takes part in every lookup ({@code generator(name)}, {@code stringGenerator(name)}, {@code get(name,
     * type)}), in {@code IdFiller}, in {@code describe()} (strategy {@code custom}, details {@code class}), in counting
     * and in {@link Disabled}.
     */
    final class Custom implements GeneratorSpec {
        private final String reference;
        private final List<CodecSpec> codec;

        private Custom(Builder builder) {
            this.reference = builder.reference;
            this.codec = builder.codec;
        }

        /**
         * A builder of a generator by reference.
         *
         * @param reference the name the runtime resolves through {@code RuntimeOptions.generators()}; in Spring a bean name
         * @return the builder
         */
        public static Builder builder(String reference) {
            return new Builder(reference);
        }

        /**
         * The name the runtime resolves through {@code RuntimeOptions.generators()}; in Spring a bean name.
         *
         * @return the reference
         */
        public String reference() {
            return reference;
        }

        /**
         * The codec list of its text ids, applied in order; as for a {@link Segment}, without a layout, so neither a
         * {@link CodecSpec.Friendly} nor a {@link CodecSpec.DatePrefix} from the id time.
         *
         * @return the codec list; default empty, for none
         */
        public List<CodecSpec> codec() {
            return codec;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Custom that && reference.equals(that.reference) && codec.equals(that.codec);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reference, codec);
        }

        @Override
        public String toString() {
            return "Custom[reference=" + reference + ", codec=" + codec + "]";
        }

        /**
         * Builds a {@link Custom}.
         */
        public static final class Builder {
            private final String reference;
            private List<CodecSpec> codec = List.of();

            private Builder(String reference) {
                this.reference = Objects.requireNonNull(reference, "reference");
            }

            /**
             * Sets the codec list of its text ids, applied in order.
             *
             * @param codec the codec list; default empty, for none
             * @return this builder
             */
            public Builder codec(List<CodecSpec> codec) {
                this.codec = List.copyOf(Objects.requireNonNull(codec, "codec"));
                return this;
            }

            /**
             * Builds the generator.
             *
             * @return the generator
             */
            public Custom build() {
                return new Custom(this);
            }
        }
    }

    /**
     * A user {@code long} generator instance, for specs built in code.
     *
     * <p>The runtime does not own it (D41): it does not close, renew nor prefetch it, and it needs no store nor machine
     * slot. It takes part in every lookup ({@code generator(name)}, {@code stringGenerator(name)}, {@code get(name,
     * type)}), in {@code IdFiller}, in {@code describe()} (strategy {@code custom}, details {@code class}), in counting
     * and in {@link Disabled}.
     */
    final class Instance implements GeneratorSpec {
        private final IdGenerator ids;
        private final List<CodecSpec> codec;

        private Instance(Builder builder) {
            this.ids = builder.ids;
            this.codec = builder.codec;
        }

        /**
         * A builder of a generator instance.
         *
         * @param ids the generator
         * @return the builder
         */
        public static Builder builder(IdGenerator ids) {
            return new Builder(ids);
        }

        /**
         * The generator.
         *
         * @return the ids
         */
        public IdGenerator ids() {
            return ids;
        }

        /**
         * The codec list of its text ids, applied in order; as for a {@link Segment}, without a layout, so neither a
         * {@link CodecSpec.Friendly} nor a {@link CodecSpec.DatePrefix} from the id time.
         *
         * @return the codec list; default empty, for none
         */
        public List<CodecSpec> codec() {
            return codec;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Instance that && ids.equals(that.ids) && codec.equals(that.codec);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ids, codec);
        }

        @Override
        public String toString() {
            return "Instance[ids=" + ids + ", codec=" + codec + "]";
        }

        /**
         * Builds a {@link Instance}.
         */
        public static final class Builder {
            private final IdGenerator ids;
            private List<CodecSpec> codec = List.of();

            private Builder(IdGenerator ids) {
                this.ids = Objects.requireNonNull(ids, "ids");
            }

            /**
             * Sets the codec list of its text ids, applied in order.
             *
             * @param codec the codec list; default empty, for none
             * @return this builder
             */
            public Builder codec(List<CodecSpec> codec) {
                this.codec = List.copyOf(Objects.requireNonNull(codec, "codec"));
                return this;
            }

            /**
             * Builds the generator.
             *
             * @return the generator
             */
            public Instance build() {
                return new Instance(this);
            }
        }
    }

    /**
     * UUIDv7 ids (D46, RFC 9562 §5.7): {@link me.ahoo.cosid.time.UuidV7Generator}, ordered within the instance and
     * unique without coordination. It needs no store, machine slot nor lease. Its text is the canonical 36-character form
     * (other text codecs may come later); the runtime hands it out by {@code get(name, UuidV7Generator.class)} and its
     * text by {@code text(name)}, and {@code describe()} names its strategy {@code uuidv7}.
     */
    final class UuidV7 implements GeneratorSpec {
        private UuidV7(Builder builder) {
        }

        /**
         * A builder of a UUIDv7 generator.
         *
         * @return the builder
         */
        public static Builder builder() {
            return new Builder();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof UuidV7;
        }

        @Override
        public int hashCode() {
            return UuidV7.class.hashCode();
        }

        @Override
        public String toString() {
            return "UuidV7[]";
        }

        /**
         * Builds a {@link UuidV7}; it has no settings yet, and may gain some with defaults.
         */
        public static final class Builder {
            private Builder() {
            }

            /**
             * Builds the generator.
             *
             * @return the generator
             */
            public UuidV7 build() {
                return new UuidV7(this);
            }
        }
    }

    /**
     * A declared generator that is not created (D24, 07-runtime-and-spring.md §4.1): looking it up throws
     * {@link me.ahoo.cosid.GeneratorDisabledException}. It is not validated and needs no store or machine slot.
     *
     * @param generator the generator it would be
     */
    record Disabled(GeneratorSpec generator) implements GeneratorSpec {
        public Disabled {
            Objects.requireNonNull(generator, "generator");
            if (generator instanceof Disabled) {
                throw new IllegalArgumentException("A disabled generator must not wrap another disabled one");
            }
        }
    }
}
