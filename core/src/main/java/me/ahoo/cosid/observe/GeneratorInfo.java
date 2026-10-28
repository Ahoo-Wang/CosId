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

package me.ahoo.cosid.observe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What {@code CosIdRuntime.describe()} tells about a generator; replaces the 4.x {@code Stat} types. Immutable; built
 * with {@link #builder(String, String)}, the only way to create one, so fields can be added with a default
 * (api-freeze.md §4).
 */
public final class GeneratorInfo {
    private final String name;
    private final String strategy;
    private final String store;
    private final Map<String, Object> details;

    private GeneratorInfo(Builder builder) {
        this.name = builder.name;
        this.strategy = builder.strategy;
        this.store = builder.store;
        this.details = Map.copyOf(builder.details);
    }

    /**
     * A builder of a generator description.
     *
     * @param name     the generator name
     * @param strategy {@code snowflake}, {@code cosid80}, {@code segment} or {@code periodic-segment}
     * @return the builder
     */
    public static Builder builder(String name, String strategy) {
        return new Builder(name, strategy);
    }

    /**
     * The generator name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * The strategy: {@code snowflake}, {@code cosid80}, {@code segment} or {@code periodic-segment}.
     *
     * @return the strategy
     */
    public String strategy() {
        return strategy;
    }

    /**
     * The store name.
     *
     * @return the store name; empty (the default) for time-based generators
     */
    public String store() {
        return store;
    }

    /**
     * Layout, slot, window, ready range headroom, batch size and the like.
     *
     * @return the details, unmodifiable; default none
     */
    public Map<String, Object> details() {
        return details;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GeneratorInfo that && name.equals(that.name) && strategy.equals(that.strategy) && store.equals(that.store)
            && details.equals(that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, strategy, store, details);
    }

    @Override
    public String toString() {
        return "GeneratorInfo[name=" + name + ", strategy=" + strategy + ", store=" + store + ", details=" + details + "]";
    }

    /**
     * Builds a {@link GeneratorInfo}.
     */
    public static final class Builder {
        private final String name;
        private final String strategy;
        private String store = "";
        private final Map<String, Object> details = new LinkedHashMap<>();

        private Builder(String name, String strategy) {
            this.name = Objects.requireNonNull(name, "name");
            this.strategy = Objects.requireNonNull(strategy, "strategy");
        }

        /**
         * Sets the store name.
         *
         * @param store the store name; default empty, for time-based generators
         * @return this builder
         */
        public Builder store(String store) {
            this.store = Objects.requireNonNull(store, "store");
            return this;
        }

        /**
         * Sets a detail.
         *
         * @param key   the detail key
         * @param value the value
         * @return this builder
         */
        public Builder detail(String key, Object value) {
            details.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Sets details, keeping those set before under other keys.
         *
         * @param details the details
         * @return this builder
         */
        public Builder details(Map<String, ?> details) {
            Objects.requireNonNull(details, "details").forEach(this::detail);
            return this;
        }

        /**
         * Builds the description.
         *
         * @return the description
         */
        public GeneratorInfo build() {
            return new GeneratorInfo(this);
        }
    }
}
