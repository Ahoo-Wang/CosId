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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A store of a {@link RuntimeSpec}, declared once and referred to by name. Immutable; built with
 * {@link #builder(String)}, the only way to create one, so fields can be added with a default (api-freeze.md §4).
 */
public final class StoreSpec {
    /** The default operation timeout: 2s. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    private final String type;
    private final Duration timeout;
    private final Map<String, Object> options;

    private StoreSpec(Builder builder) {
        this.type = Objects.requireNonNull(builder.type, "type");
        this.timeout = Objects.requireNonNull(builder.timeout, "timeout");
        this.options = Map.copyOf(builder.options);
    }

    /**
     * A builder of a store.
     *
     * @param type the store type, matched to a {@code StoreProvider}
     * @return the builder
     */
    public static Builder builder(String type) {
        return new Builder(type);
    }

    /**
     * The store type, matched to a {@code StoreProvider}.
     *
     * @return the type
     */
    public String type() {
        return type;
    }

    /**
     * The operation timeout.
     *
     * @return the timeout; default {@link #DEFAULT_TIMEOUT}
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * The backend options.
     *
     * @return the options, unmodifiable
     */
    public Map<String, Object> options() {
        return options;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StoreSpec that && type.equals(that.type) && timeout.equals(that.timeout) && options.equals(that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, timeout, options);
    }

    @Override
    public String toString() {
        return "StoreSpec[type=" + type + ", timeout=" + timeout + ", options=" + options + "]";
    }

    /**
     * Builds a {@link StoreSpec}.
     */
    public static final class Builder {
        private final String type;
        private Duration timeout = DEFAULT_TIMEOUT;
        private final Map<String, Object> options = new LinkedHashMap<>();

        private Builder(String type) {
            this.type = Objects.requireNonNull(type, "type");
        }

        /**
         * Sets the operation timeout.
         *
         * @param timeout the timeout; default {@link #DEFAULT_TIMEOUT}
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
         * Builds the store.
         *
         * @return the store
         */
        public StoreSpec build() {
            return new StoreSpec(this);
        }
    }
}
