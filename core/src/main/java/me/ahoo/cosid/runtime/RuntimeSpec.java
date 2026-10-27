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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything a {@link CosIdRuntime} starts from (07-runtime-and-spring.md §1). Spring, the server, the migration tool
 * and hand-written code all build one.
 *
 * @param namespace  the namespace of the range keys and slot leases
 * @param stores     the stores by name
 * @param machine    the machine slot; required when a time-based generator exists
 * @param generators the generators by name; the one named {@value #DEFAULT_GENERATOR} is the 4.x share
 */
public record RuntimeSpec(String namespace,
                          Map<String, StoreSpec> stores,
                          Optional<MachineSpec> machine,
                          Map<String, GeneratorSpec> generators) {
    public static final String DEFAULT_GENERATOR = "default";

    public RuntimeSpec {
        Objects.requireNonNull(namespace, "namespace");
        if (namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        stores = Map.copyOf(Objects.requireNonNull(stores, "stores"));
        Objects.requireNonNull(machine, "machine");
        generators = Map.copyOf(Objects.requireNonNull(generators, "generators"));
    }
}
