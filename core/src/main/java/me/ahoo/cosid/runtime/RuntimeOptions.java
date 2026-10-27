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

import me.ahoo.cosid.codec.IdCodec;
import me.ahoo.cosid.internal.ClassNameCodecs;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.store.StoreProvider;
import me.ahoo.cosid.time.TimeSource;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * What a {@link CosIdRuntime} starts with besides its {@link RuntimeSpec}: everything that is not configuration.
 *
 * @param time           the time source
 * @param executor       runs refills and renewals; when empty the runtime creates a virtual thread executor and
 *                       closes it
 * @param listener       the event listener
 * @param storeProviders providers besides those {@link java.util.ServiceLoader} finds; they win for the same type
 * @param clients        existing client objects by store name, passed as {@code StoreConfig.client}
 * @param hostResolver   the host of a derived owner key
 * @param port           the port of a derived owner key, {@code host:port}; {@code host:pid} when empty
 * @param codecs         resolves {@link CodecSpec.Custom} references; empty for an unknown one
 * @param countGenerated count the ids each generator issues through the views the runtime hands out
 *                       ({@link CosIdRuntime#generated(String)}); off by default, which leaves the hot path unchanged
 */
public record RuntimeOptions(TimeSource time,
                             Optional<Executor> executor,
                             GeneratorListener listener,
                             List<StoreProvider> storeProviders,
                             Map<String, Object> clients,
                             HostResolver hostResolver,
                             OptionalInt port,
                             Function<String, Optional<IdCodec>> codecs,
                             boolean countGenerated) {
    public RuntimeOptions {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        storeProviders = List.copyOf(Objects.requireNonNull(storeProviders, "storeProviders"));
        clients = Map.copyOf(Objects.requireNonNull(clients, "clients"));
        Objects.requireNonNull(hostResolver, "hostResolver");
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(codecs, "codecs");
    }

    /**
     * Options without counting.
     *
     * @param time           the time source
     * @param executor       the executor, or empty for an own one
     * @param listener       the event listener
     * @param storeProviders providers besides those {@link java.util.ServiceLoader} finds
     * @param clients        existing client objects by store name
     * @param hostResolver   the host of a derived owner key
     * @param port           the port of a derived owner key
     * @param codecs         resolves {@link CodecSpec.Custom} references
     */
    public RuntimeOptions(TimeSource time, Optional<Executor> executor, GeneratorListener listener, List<StoreProvider> storeProviders,
                          Map<String, Object> clients, HostResolver hostResolver, OptionalInt port,
                          Function<String, Optional<IdCodec>> codecs) {
        this(time, executor, listener, storeProviders, clients, hostResolver, port, codecs, false);
    }

    /**
     * These options, counting the generated ids or not.
     *
     * @param count whether to count
     * @return the options
     */
    public RuntimeOptions withCountGenerated(boolean count) {
        return new RuntimeOptions(time, executor, listener, storeProviders, clients, hostResolver, port, codecs, count);
    }

    /**
     * The system clock, an own executor, no listener, providers from {@link java.util.ServiceLoader} only, no
     * clients, the local host, no port, custom codecs by class name, no counting.
     *
     * @return the default options
     */
    public static RuntimeOptions defaults() {
        return new RuntimeOptions(TimeSource.SYSTEM, Optional.empty(), GeneratorListener.NOOP, List.of(), Map.of(),
            HostResolver.LOCAL, OptionalInt.empty(), ClassNameCodecs::resolve);
    }
}
