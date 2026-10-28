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

package me.ahoo.cosid.store;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a {@link StoreProvider} opens a store with (06-stores.md §1). Immutable; built with
 * {@link #builder(String, String)}, the only way to create one, so fields can be added with a default
 * (api-freeze.md §4).
 */
public final class StoreConfig {
    /** The default operation timeout: 2s. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    private final String name;
    private final String type;
    private final Duration timeout;
    private final Map<String, Object> options;
    private final Optional<Object> client;

    private StoreConfig(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.timeout = builder.timeout;
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
        this.options = Map.copyOf(builder.options);
        this.client = Optional.ofNullable(builder.client);
    }

    /**
     * A builder of a store config.
     *
     * @param name the store name in the runtime spec
     * @param type the store type
     * @return the builder
     */
    public static Builder builder(String name, String type) {
        return new Builder(name, type);
    }

    /**
     * The store name in the runtime spec.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * The store type.
     *
     * @return the type
     */
    public String type() {
        return type;
    }

    /**
     * The operation timeout, enforced by the backend's own mechanism.
     *
     * @return the timeout, positive; default {@link #DEFAULT_TIMEOUT}
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * The backend options, such as {@code initSchema}.
     *
     * @return the options, unmodifiable; default none
     */
    public Map<String, Object> options() {
        return options;
    }

    /**
     * An existing connection object ({@code DataSource}, a Lettuce connection, {@code MongoClient},
     * {@code CuratorFramework}).
     *
     * @return the client; when empty (the default) the provider creates its own from the options and closes it
     */
    public Optional<Object> client() {
        return client;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StoreConfig that && name.equals(that.name) && type.equals(that.type) && timeout.equals(that.timeout)
            && options.equals(that.options) && client.equals(that.client);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, timeout, options, client);
    }

    @Override
    public String toString() {
        return "StoreConfig[name=" + name + ", type=" + type + ", timeout=" + timeout + ", options=" + options + ", client=" + client + "]";
    }

    /**
     * Builds a {@link StoreConfig}; {@link #build()} checks the fields.
     */
    public static final class Builder {
        private final String name;
        private final String type;
        private Duration timeout = DEFAULT_TIMEOUT;
        private final Map<String, Object> options = new LinkedHashMap<>();
        private Object client;

        private Builder(String name, String type) {
            this.name = Objects.requireNonNull(name, "name");
            this.type = Objects.requireNonNull(type, "type");
        }

        /**
         * Sets the operation timeout, enforced by the backend's own mechanism.
         *
         * @param timeout the timeout, positive; default {@link StoreConfig#DEFAULT_TIMEOUT}
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /**
         * Sets a backend option.
         *
         * @param key   the option key
         * @param value the value
         * @return this builder
         */
        public Builder option(String key, Object value) {
            options.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Sets backend options, keeping those set before under other keys.
         *
         * @param options the options
         * @return this builder
         */
        public Builder options(Map<String, ?> options) {
            Objects.requireNonNull(options, "options").forEach(this::option);
            return this;
        }

        /**
         * Sets an existing connection object; the provider does not close it.
         *
         * @param client the client; by default the provider creates its own from the options and closes it
         * @return this builder
         */
        public Builder client(Object client) {
            this.client = Objects.requireNonNull(client, "client");
            return this;
        }

        /**
         * Builds the config.
         *
         * @return the config
         * @throws IllegalArgumentException if the timeout is not positive
         */
        public StoreConfig build() {
            return new StoreConfig(this);
        }
    }
}
