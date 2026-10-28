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
import me.ahoo.cosid.codec.IdCodec;
import me.ahoo.cosid.internal.ClassNameCodecs;
import me.ahoo.cosid.observe.GeneratorListener;
import me.ahoo.cosid.store.StoreProvider;
import me.ahoo.cosid.time.TimeSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * What a {@link CosIdRuntime} starts with besides its {@link RuntimeSpec}: everything that is not configuration.
 * Immutable; built with {@link #builder()}, the only way to create one, so fields can be added with a default
 * (api-freeze.md §4); {@link #toBuilder()} changes one.
 */
public final class RuntimeOptions {
    private final TimeSource time;
    private final Optional<Executor> executor;
    private final GeneratorListener listener;
    private final List<StoreProvider> storeProviders;
    private final Map<String, Object> clients;
    private final HostResolver hostResolver;
    private final OptionalInt port;
    private final Function<String, Optional<IdCodec>> codecs;
    private final Function<String, Optional<IdGenerator>> generators;
    private final boolean countGenerated;

    private RuntimeOptions(Builder builder) {
        this.time = builder.time;
        this.executor = Optional.ofNullable(builder.executor);
        this.listener = builder.listener;
        this.storeProviders = List.copyOf(builder.storeProviders);
        this.clients = Map.copyOf(builder.clients);
        this.hostResolver = builder.hostResolver;
        this.port = builder.port;
        this.codecs = builder.codecs;
        this.generators = builder.generators;
        this.countGenerated = builder.countGenerated;
    }

    /**
     * A builder starting from the defaults ({@link #defaults()}).
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The system clock, an own executor, no listener, providers from {@link java.util.ServiceLoader} only, no
     * clients, the local host, no port, custom codecs by class name, no custom generators, no counting.
     *
     * @return the default options
     */
    public static RuntimeOptions defaults() {
        return builder().build();
    }

    /**
     * A builder holding these options, to change them.
     *
     * @return the builder
     */
    public Builder toBuilder() {
        Builder builder = new Builder().time(time).listener(listener).hostResolver(hostResolver).codecs(codecs).generators(generators)
            .countGenerated(countGenerated);
        executor.ifPresent(builder::executor);
        builder.storeProviders.addAll(storeProviders);
        builder.clients.putAll(clients);
        builder.port = port;
        return builder;
    }

    /**
     * The time source.
     *
     * @return the time source; default {@link TimeSource#SYSTEM}
     */
    public TimeSource time() {
        return time;
    }

    /**
     * Runs refills and renewals.
     *
     * @return the executor; when empty (the default) the runtime creates a virtual thread executor and closes it
     */
    public Optional<Executor> executor() {
        return executor;
    }

    /**
     * The event listener.
     *
     * @return the listener; default {@link GeneratorListener#NOOP}
     */
    public GeneratorListener listener() {
        return listener;
    }

    /**
     * Providers besides those {@link java.util.ServiceLoader} finds; they win for the same type.
     *
     * @return the providers, unmodifiable; default none
     */
    public List<StoreProvider> storeProviders() {
        return storeProviders;
    }

    /**
     * Existing client objects by store name, passed as {@code StoreConfig.client}.
     *
     * @return the clients, unmodifiable; default none
     */
    public Map<String, Object> clients() {
        return clients;
    }

    /**
     * The host of a derived owner key.
     *
     * @return the resolver; default {@link HostResolver#LOCAL}
     */
    public HostResolver hostResolver() {
        return hostResolver;
    }

    /**
     * The port of a derived owner key, {@code host:port}.
     *
     * @return the port; when empty (the default) the owner key is {@code host:pid}
     */
    public OptionalInt port() {
        return port;
    }

    /**
     * Resolves {@link CodecSpec.Custom} references.
     *
     * @return the resolver, empty for an unknown reference; default by class name
     */
    public Function<String, Optional<IdCodec>> codecs() {
        return codecs;
    }

    /**
     * Resolves {@link GeneratorSpec.Custom} references (D41).
     *
     * @return the resolver, empty for an unknown reference; by default always empty, so a custom reference fails the
     *     start
     */
    public Function<String, Optional<IdGenerator>> generators() {
        return generators;
    }

    /**
     * Whether to count the ids each generator issues through the views the runtime hands out
     * ({@link CosIdRuntime#generated(String)}).
     *
     * @return whether to count; off by default, which leaves the hot path unchanged
     */
    public boolean countGenerated() {
        return countGenerated;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RuntimeOptions that && time.equals(that.time) && executor.equals(that.executor) && listener.equals(that.listener)
            && storeProviders.equals(that.storeProviders) && clients.equals(that.clients) && hostResolver.equals(that.hostResolver)
            && port.equals(that.port) && codecs.equals(that.codecs) && generators.equals(that.generators) && countGenerated == that.countGenerated;
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, executor, listener, storeProviders, clients, hostResolver, port, codecs, generators, countGenerated);
    }

    @Override
    public String toString() {
        return "RuntimeOptions[time=" + time + ", executor=" + executor + ", listener=" + listener + ", storeProviders=" + storeProviders
            + ", clients=" + clients + ", hostResolver=" + hostResolver + ", port=" + port + ", codecs=" + codecs
            + ", generators=" + generators + ", countGenerated=" + countGenerated + "]";
    }

    /**
     * Builds {@link RuntimeOptions}.
     */
    public static final class Builder {
        private TimeSource time = TimeSource.SYSTEM;
        private Executor executor;
        private GeneratorListener listener = GeneratorListener.NOOP;
        private final List<StoreProvider> storeProviders = new ArrayList<>();
        private final Map<String, Object> clients = new LinkedHashMap<>();
        private HostResolver hostResolver = HostResolver.LOCAL;
        private OptionalInt port = OptionalInt.empty();
        private Function<String, Optional<IdCodec>> codecs = ClassNameCodecs::resolve;
        private Function<String, Optional<IdGenerator>> generators = reference -> Optional.empty();
        private boolean countGenerated;

        private Builder() {
        }

        /**
         * Sets the time source.
         *
         * @param time the time source; default {@link TimeSource#SYSTEM}
         * @return this builder
         */
        public Builder time(TimeSource time) {
            this.time = Objects.requireNonNull(time, "time");
            return this;
        }

        /**
         * Sets the executor of refills and renewals; the runtime does not close it.
         *
         * @param executor the executor; by default the runtime creates a virtual thread executor and closes it
         * @return this builder
         */
        public Builder executor(Executor executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        /**
         * Sets the event listener.
         *
         * @param listener the listener; default {@link GeneratorListener#NOOP}
         * @return this builder
         */
        public Builder listener(GeneratorListener listener) {
            this.listener = Objects.requireNonNull(listener, "listener");
            return this;
        }

        /**
         * Adds a provider besides those {@link java.util.ServiceLoader} finds; it wins for the same type, and of two
         * added for one type the first wins.
         *
         * @param storeProvider the provider
         * @return this builder
         */
        public Builder storeProvider(StoreProvider storeProvider) {
            storeProviders.add(Objects.requireNonNull(storeProvider, "storeProvider"));
            return this;
        }

        /**
         * Sets an existing client object of a store, passed as {@code StoreConfig.client}.
         *
         * @param store  the store name
         * @param client the client object
         * @return this builder
         */
        public Builder client(String store, Object client) {
            clients.put(Objects.requireNonNull(store, "store"), Objects.requireNonNull(client, "client"));
            return this;
        }

        /**
         * Sets the host of a derived owner key.
         *
         * @param hostResolver the resolver; default {@link HostResolver#LOCAL}
         * @return this builder
         */
        public Builder hostResolver(HostResolver hostResolver) {
            this.hostResolver = Objects.requireNonNull(hostResolver, "hostResolver");
            return this;
        }

        /**
         * Sets the port of a derived owner key, {@code host:port}.
         *
         * @param port the port; by default the owner key is {@code host:pid}
         * @return this builder
         */
        public Builder port(int port) {
            this.port = OptionalInt.of(port);
            return this;
        }

        /**
         * Sets how {@link CodecSpec.Custom} references resolve.
         *
         * @param codecs the resolver, empty for an unknown reference; default by class name
         * @return this builder
         */
        public Builder codecs(Function<String, Optional<IdCodec>> codecs) {
            this.codecs = Objects.requireNonNull(codecs, "codecs");
            return this;
        }

        /**
         * Sets how {@link GeneratorSpec.Custom} references resolve (D41).
         *
         * @param generators the resolver, empty for an unknown reference; by default always empty
         * @return this builder
         */
        public Builder generators(Function<String, Optional<IdGenerator>> generators) {
            this.generators = Objects.requireNonNull(generators, "generators");
            return this;
        }

        /**
         * Sets whether to count the ids each generator issues through the views the runtime hands out
         * ({@link CosIdRuntime#generated(String)}).
         *
         * @param countGenerated whether to count; off by default, which leaves the hot path unchanged
         * @return this builder
         */
        public Builder countGenerated(boolean countGenerated) {
            this.countGenerated = countGenerated;
            return this;
        }

        /**
         * Builds the options.
         *
         * @return the options
         */
        public RuntimeOptions build() {
            return new RuntimeOptions(this);
        }
    }
}
