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

import me.ahoo.cosid.GeneratorDisabledException;
import me.ahoo.cosid.GroupedIdGenerator;
import me.ahoo.cosid.PartsIdGenerator;
import me.ahoo.cosid.codec.GroupedCodec;
import me.ahoo.cosid.codec.PartsCodec;
import me.ahoo.cosid.observe.GeneratorInfo;
import me.ahoo.cosid.runtime.CosIdRuntime;

import org.springframework.boot.actuate.endpoint.InvalidEndpointRequestException;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The read-only {@code cosid} Actuator endpoint (07 §4.2, D17), replacing the 4.x {@code cosid},
 * {@code cosidGenerator} and {@code cosidStringGenerator} endpoints:
 * <ul>
 *     <li>{@code GET /actuator/cosid}: every generator ({@link CosIdRuntime#describe()});</li>
 *     <li>{@code GET /actuator/cosid/{name}}: one generator;</li>
 *     <li>{@code GET /actuator/cosid/{name}/long}: a {@code long} id ({@code snowflake}, {@code segment} and {@code custom});</li>
 *     <li>{@code GET /actuator/cosid/{name}/string}: a text id, through the generator's codec.</li>
 * </ul>
 * An unknown generator answers 404; a disabled one, or {@code long} of a text-only one, 400.
 */
@Endpoint(id = CosIdEndpoint.ID)
public class CosIdEndpoint {
    public static final String ID = "cosid";
    public static final String LONG = "long";
    public static final String STRING = "string";

    private final CosIdRuntime runtime;

    public CosIdEndpoint(CosIdRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Describes every generator.
     *
     * @return the generators by name
     */
    @ReadOperation
    public Map<String, Generator> describe() {
        Map<String, Generator> generators = new LinkedHashMap<>();
        runtime.describe().forEach((name, info) -> generators.put(name, Generator.of(info)));
        return generators;
    }

    /**
     * Describes one generator.
     *
     * @param name the generator name
     * @return the generator, or null for an unknown one
     */
    @ReadOperation
    public Generator generator(@Selector String name) {
        GeneratorInfo info = runtime.describe().get(name);
        return info == null ? null : Generator.of(info);
    }

    /**
     * Generates an id.
     *
     * @param name   the generator name
     * @param format {@code long} or {@code string}
     * @return the id, or null for an unknown generator
     */
    @ReadOperation
    public Object generate(@Selector String name, @Selector String format) {
        GeneratorInfo info = runtime.describe().get(name);
        if (info == null) {
            return null;
        }
        try {
            return switch (format) {
                case LONG -> {
                    if (!CosIdSpecs.SNOWFLAKE.equals(info.strategy()) && !CosIdSpecs.SEGMENT.equals(info.strategy())
                        && !CosIdSpecs.CUSTOM.equals(info.strategy())) {
                        throw new InvalidEndpointRequestException("Generator [" + name + "] (" + info.strategy() + ") issues no long ids",
                            "no long ids");
                    }
                    yield runtime.generator(name).generate();
                }
                case STRING -> switch (info.strategy()) {
                    case CosIdSpecs.COSID80 -> runtime.get(name, PartsCodec.class).encode(runtime.get(name, PartsIdGenerator.class).nextParts());
                    case CosIdSpecs.PERIODIC_SEGMENT -> runtime.get(name, GroupedCodec.class).encode(runtime.get(name, GroupedIdGenerator.class).generate());
                    default -> runtime.stringGenerator(name).generate();
                };
                default -> throw new InvalidEndpointRequestException("Unknown format [" + format + "]: long or string", "unknown format");
            };
        } catch (GeneratorDisabledException e) {
            throw new InvalidEndpointRequestException(e.getMessage(), "disabled");
        }
    }

    /**
     * The JSON body of a generator: {@link GeneratorInfo}, which has no bean accessors for Jackson, as a record.
     *
     * @param name     the generator name
     * @param strategy {@code snowflake}, {@code cosid80}, {@code segment} or {@code periodic-segment}
     * @param store    the store name, empty for time-based generators
     * @param details  layout, slot, window, ready range headroom, batch size and the like
     */
    public record Generator(String name, String strategy, String store, Map<String, Object> details) {
        public Generator {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(strategy, "strategy");
            Objects.requireNonNull(store, "store");
            details = Map.copyOf(Objects.requireNonNull(details, "details"));
        }

        static Generator of(GeneratorInfo info) {
            return new Generator(info.name(), info.strategy(), info.store(), info.details());
        }
    }
}
