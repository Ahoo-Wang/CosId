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

package me.ahoo.cosid.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * The {@code cosid.*} configuration (07-runtime-and-spring.md §4.1), bound once and immutable. {@link CosIdSpecs#toSpec}
 * maps it to a {@code RuntimeSpec}; the runtime validates the rest.
 *
 * @param enabled    the master switch; default true
 * @param namespace  the namespace of the runtime; default {@code cosid}
 * @param stores     the stores by name, each declared once and referenced by name
 * @param machine    the machine slot, needed by time-based generators
 * @param generators the generators by name; {@code default} is the one injected without a qualifier
 * @param metrics    the Micrometer metrics
 */
@ConfigurationProperties(prefix = CosIdProperties.PREFIX)
public record CosIdProperties(@DefaultValue("true") boolean enabled,
                              @DefaultValue(DEFAULT_NAMESPACE) String namespace,
                              Map<String, Store> stores,
                              Machine machine,
                              Map<String, Generator> generators,
                              @DefaultValue Metrics metrics) {
    public static final String PREFIX = "cosid";
    public static final String DEFAULT_NAMESPACE = "cosid";

    public CosIdProperties {
        stores = stores == null ? Map.of() : Map.copyOf(stores);
        generators = generators == null ? Map.of() : Map.copyOf(generators);
        metrics = metrics == null ? new Metrics(false) : metrics;
    }

    /**
     * A store.
     *
     * @param type    the store type: {@code jdbc}, {@code redis}, {@code mongo}, {@code zookeeper} or a {@code StoreProvider}
     *                bean's
     * @param timeout the operation timeout; default 2s, at most the segment {@code reserve-timeout}
     * @param client  the name of the bean to use as the client; without it the single bean of the store type's client
     *                type in the context ({@code DataSource}, Lettuce or {@code RedisConnectionFactory}, {@code MongoClient},
     *                {@code CuratorFramework}), if any
     * @param options backend options, such as {@code initSchema}, {@code uri} or {@code wait}
     */
    public record Store(String type,
                        @DefaultValue("2s") Duration timeout,
                        String client,
                        Map<String, String> options) {
        public Store {
            options = options == null ? Map.of() : Map.copyOf(options);
        }
    }

    /**
     * The machine slot: exactly one of {@code leased} or {@code static}.
     *
     * @param leased a slot leased from a store
     * @param fixed  a slot the deployment keeps unique ({@code cosid.machine.static})
     */
    public record Machine(Leased leased, @Name("static") Static fixed) {
    }

    /**
     * A slot leased from a store.
     *
     * @param store         the store name
     * @param bits          the slots are {@code [0, 2^bits)}; default 10
     * @param ttl           the lease time to live; default 30s
     * @param tolerance     the window ceiling lies this far beyond the expiry (D20); default 1s
     * @param preferredSlot the slot to try first, such as a StatefulSet ordinal; empty for none
     * @param ownerKey      the owner key; {@code host:port} or {@code host:pid} when empty (07 §1.1)
     */
    public record Leased(String store,
                         @DefaultValue("10") int bits,
                         @DefaultValue("30s") Duration ttl,
                         @DefaultValue("1s") Duration tolerance,
                         Integer preferredSlot,
                         String ownerKey) {
    }

    /**
     * A slot the deployment keeps unique.
     *
     * @param bits      the slot bits; default 10
     * @param slot      the slot
     * @param stateFile the window file; none means no clock regression protection across restarts
     * @param ahead     how far ahead the window ceiling is written; default 10s
     * @param notBefore the floor while the state file does not exist yet (given by the migration tool)
     */
    public record Static(@DefaultValue("10") int bits,
                         Integer slot,
                         Path stateFile,
                         @DefaultValue("10s") Duration ahead,
                         Instant notBefore) {
    }

    /**
     * A generator. Which fields apply depends on the strategy.
     *
     * @param strategy      {@code snowflake}, {@code cosid80}, {@code segment} or {@code periodic-segment}
     * @param disabled      declared but not created (D24): its bean throws {@code GeneratorDisabledException}
     * @param layout        snowflake: the layout preset, {@code SNOWFLAKE_MS} (default), {@code SNOWFLAKE_S},
     *                      {@code JS_SAFE_MS} or {@code JS_SAFE_S}
     * @param epoch         snowflake: overrides the preset's epoch
     * @param unit          snowflake: overrides the preset's time unit, {@code MILLIS} or {@code SECONDS}
     * @param timeBits      snowflake: overrides the preset's time bits
     * @param nodeBits      snowflake: overrides the preset's node bits
     * @param seqBits       snowflake: overrides the preset's sequence bits
     * @param regression    time-based: the clock regression policy
     * @param store         segment: the store name
     * @param step          segment: the smallest reservation; default 100
     * @param initialOffset segment: where a new key starts; default 0
     * @param lookahead     segment: how long the ids at hand should last; default 1s, 0 disables prefetching
     * @param maxBatch      segment: the largest reservation; default {@code max(step * 1000, 2^20)} rounded to the step
     * @param reserveTimeout segment: how long a caller waits for a refill; default 3s
     * @param rangeTtl      segment: a range unused this long is dropped; default never
     * @param period        periodic-segment: {@code YEAR}, {@code YEAR_MONTH} or {@code YEAR_MONTH_DAY}
     * @param zone          periodic-segment: the time zone of the periods; default the system zone
     * @param prewarm       periodic-segment: the next period is warmed up this long before; default 1m
     * @param sealAfter     periodic-segment: a period's key is sealed this long after it ends; default never
     * @param codec         the codec list, applied in order; for periodic-segment a {@code period} item where the period
     *                      key goes (or a first {@code date-prefix} item: the period first), for cosid80 a single
     *                      {@code radix} or {@code friendly} item
     */
    public record Generator(String strategy,
                            boolean disabled,
                            @DefaultValue("SNOWFLAKE_MS") String layout,
                            Instant epoch,
                            ChronoUnit unit,
                            Integer timeBits,
                            Integer nodeBits,
                            Integer seqBits,
                            @DefaultValue Regression regression,
                            String store,
                            @DefaultValue("100") long step,
                            @DefaultValue("0") long initialOffset,
                            @DefaultValue("1s") Duration lookahead,
                            Long maxBatch,
                            @DefaultValue("3s") Duration reserveTimeout,
                            Duration rangeTtl,
                            String period,
                            ZoneId zone,
                            @DefaultValue("1m") Duration prewarm,
                            Duration sealAfter,
                            List<Codec> codec) {
        public Generator {
            codec = codec == null ? List.of() : List.copyOf(codec);
        }
    }

    /**
     * What a time-based generator does when the clock goes back.
     *
     * @param borrowUpTo the largest regression issued on the last time unit; default 5ms
     * @param waitUpTo   the largest regression waited out; default 1s
     */
    public record Regression(@DefaultValue("5ms") Duration borrowUpTo, @DefaultValue("1s") Duration waitUpTo) {
    }

    /**
     * An item of a codec list: exactly one of its fields.
     *
     * @param radix      turns the id into text in a base
     * @param friendly   turns a time-based id into readable text
     * @param prefix     a text prefix
     * @param suffix     a text suffix
     * @param datePrefix a date prefix; as the first item of a periodic segment without a {@code period} item, the period
     *                   prefix
     * @param custom     a codec bean name or class name
     * @param period     periodic segment only: where the period key goes; the items before it apply to the value, the
     *                   items after it decorate the text that starts with the key
     */
    public record Codec(Radix radix,
                        Friendly friendly,
                        String prefix,
                        String suffix,
                        DatePrefix datePrefix,
                        String custom,
                        PeriodText period) {
    }

    /**
     * The period key of a periodic segment in its text ids (the 4.x {@code group-prefix}).
     *
     * @param enabled   whether the text holds the period key at all; false leaves it out, and such text cannot be
     *                  decoded (4.x {@code group-prefix.enabled = false}); default true
     * @param delimiter the text between the period key and what follows; default {@code -}
     */
    public record PeriodText(@DefaultValue("true") boolean enabled, @DefaultValue("-") String delimiter) {
    }

    /**
     * A radix codec.
     *
     * @param radix    the base, 2 to 62; default 62
     * @param width    the most digits; default those of {@code Long.MAX_VALUE}
     * @param padStart pad with leading zeros to the width
     */
    public record Radix(@DefaultValue("62") int radix, Integer width, boolean padStart) {
    }

    /**
     * A friendly codec.
     *
     * @param zone     the time zone; default the system zone
     * @param padStart pad the node and sequence; default true
     */
    public record Friendly(ZoneId zone, @DefaultValue("true") boolean padStart) {
    }

    /**
     * A date prefix.
     *
     * @param pattern   the date pattern; default {@code yyyyMMdd} (unused in a periodic segment, whose period gives it)
     * @param delimiter the text between the date and the id; default {@code -}
     * @param source    {@code ID_TIME} (default) or {@code NOW}
     * @param zone      the time zone; default the system zone
     */
    public record DatePrefix(@DefaultValue("yyyyMMdd") String pattern,
                             @DefaultValue("-") String delimiter,
                             @DefaultValue("ID_TIME") me.ahoo.cosid.codec.DatePrefix.Source source,
                             ZoneId zone) {
    }

    /**
     * The Micrometer metrics.
     *
     * @param generated count the ids every generator issues ({@code cosid.generator.generated}), through every view the
     *                  runtime hands out; adds a counter to each call, so off by default
     */
    public record Metrics(@DefaultValue("false") boolean generated) {
    }
}
