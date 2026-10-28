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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything a {@link CosIdRuntime} starts from (07-runtime-and-spring.md §1). Spring, the server, the migration tool
 * and hand-written code all build one, with {@link #builder(String)}; {@link #toBuilder()} changes one. Immutable;
 * the builder is the only way to create one, so fields can be added with a default (api-freeze.md §4).
 */
public final class RuntimeSpec {
    public static final String DEFAULT_GENERATOR = "default";
    /** The name of the {@link #machine()} among the machines; no named machine may take it. */
    public static final String DEFAULT_MACHINE = "default";

    private final String namespace;
    private final Map<String, StoreSpec> stores;
    private final Optional<MachineSpec> machine;
    private final Map<String, MachineSpec> machines;
    private final Map<String, GeneratorSpec> generators;

    private RuntimeSpec(Builder builder) {
        this.namespace = builder.namespace;
        if (namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        this.stores = Map.copyOf(builder.stores);
        this.machine = Optional.ofNullable(builder.machine);
        this.machines = Map.copyOf(builder.machines);
        this.generators = Map.copyOf(builder.generators);
    }

    /**
     * A builder of a spec without stores, machine slot or generators.
     *
     * @param namespace the namespace of the range keys and slot leases, not blank
     * @return the builder
     */
    public static Builder builder(String namespace) {
        return new Builder(namespace);
    }

    /**
     * A builder holding this spec, to change it.
     *
     * @return the builder
     */
    public Builder toBuilder() {
        Builder builder = new Builder(namespace);
        builder.stores.putAll(stores);
        builder.machine = machine.orElse(null);
        builder.machines.putAll(machines);
        builder.generators.putAll(generators);
        return builder;
    }

    /**
     * The namespace of the range keys and slot leases.
     *
     * @return the namespace
     */
    public String namespace() {
        return namespace;
    }

    /**
     * The stores by name.
     *
     * @return the stores, unmodifiable
     */
    public Map<String, StoreSpec> stores() {
        return stores;
    }

    /**
     * The machine slot; required when a time-based generator exists.
     *
     * @return the machine slot, empty for none
     */
    public Optional<MachineSpec> machine() {
        return machine;
    }

    /**
     * The named machines (D37), such as a slot in a namespace shared by several services: a time-based generator
     * chooses one by name ({@code GeneratorSpec.Snowflake.machine()}); only the chosen ones start.
     *
     * @return the named machines, unmodifiable; default none
     */
    public Map<String, MachineSpec> machines() {
        return machines;
    }

    /**
     * The generators by name; the one named {@value #DEFAULT_GENERATOR} is the 4.x share.
     *
     * @return the generators, unmodifiable
     */
    public Map<String, GeneratorSpec> generators() {
        return generators;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RuntimeSpec that && namespace.equals(that.namespace) && stores.equals(that.stores)
            && machine.equals(that.machine) && machines.equals(that.machines) && generators.equals(that.generators);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, stores, machine, machines, generators);
    }

    @Override
    public String toString() {
        return "RuntimeSpec[namespace=" + namespace + ", stores=" + stores + ", machine=" + machine + ", machines=" + machines
            + ", generators=" + generators + "]";
    }

    /**
     * Builds a {@link RuntimeSpec}.
     */
    public static final class Builder {
        private final String namespace;
        private final Map<String, StoreSpec> stores = new LinkedHashMap<>();
        private MachineSpec machine;
        private final Map<String, MachineSpec> machines = new LinkedHashMap<>();
        private final Map<String, GeneratorSpec> generators = new LinkedHashMap<>();

        private Builder(String namespace) {
            this.namespace = Objects.requireNonNull(namespace, "namespace");
        }

        /**
         * Declares a store, replacing one of the same name.
         *
         * @param name  the store name
         * @param store the store
         * @return this builder
         */
        public Builder store(String name, StoreSpec store) {
            stores.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(store, "store"));
            return this;
        }

        /**
         * Sets the machine slot; required when a time-based generator exists.
         *
         * @param machine the machine slot; default none
         * @return this builder
         */
        public Builder machine(MachineSpec machine) {
            this.machine = Objects.requireNonNull(machine, "machine");
            return this;
        }

        /**
         * Declares a named machine (D37), replacing one of the same name; it starts only when an enabled time-based
         * generator chooses it.
         *
         * @param name    the machine name, not {@value RuntimeSpec#DEFAULT_MACHINE}
         * @param machine the machine; give it a namespace of its own ({@code namespace(...)})
         * @return this builder
         */
        public Builder machine(String name, MachineSpec machine) {
            machines.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(machine, "machine"));
            return this;
        }

        /**
         * Declares a generator, replacing one of the same name.
         *
         * @param name      the generator name; {@value RuntimeSpec#DEFAULT_GENERATOR} is the 4.x share
         * @param generator the generator
         * @return this builder
         */
        public Builder generator(String name, GeneratorSpec generator) {
            generators.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(generator, "generator"));
            return this;
        }

        /**
         * Removes a generator, if declared.
         *
         * @param name the generator name
         * @return this builder
         */
        public Builder removeGenerator(String name) {
            generators.remove(Objects.requireNonNull(name, "name"));
            return this;
        }

        /**
         * Builds the spec.
         *
         * @return the spec
         * @throws IllegalArgumentException if the namespace is blank
         */
        public RuntimeSpec build() {
            return new RuntimeSpec(this);
        }
    }
}
