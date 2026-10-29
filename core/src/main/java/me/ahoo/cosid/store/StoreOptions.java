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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The options of a store matched to the keys its provider declares (R1-spi-01, R1-spring-01): case, {@code -} and
 * {@code _} do not count, so an environment variable ({@code ..._OPTIONS_INITSCHEMA}) and kebab-case
 * ({@code init-schema}) reach {@code initSchema}; any other key is a problem. The runtime applies it to every store
 * it opens; code that opens a store itself applies it too.
 */
public final class StoreOptions {
    private StoreOptions() {
    }

    /**
     * A key as it is compared: lower case, without {@code -} and {@code _}.
     *
     * @param key the key
     * @return the compared form
     */
    public static String canonical(String key) {
        return key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    /**
     * The options under the provider's spelling, or an error naming every unknown or repeated key.
     *
     * @param store    the store name, for the error
     * @param provider the provider that opens the store
     * @param options  the configured options
     * @return the options keyed as {@link StoreProvider#options()} spells them
     * @throws IllegalArgumentException if a key matches no declared option, or two keys match one
     */
    public static Map<String, Object> resolve(String store, StoreProvider provider, Map<String, Object> options) {
        List<String> problems = new ArrayList<>();
        Map<String, Object> resolved = resolve(store, provider.type(), provider.options(), options, problems);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        return resolved;
    }

    /**
     * The options under the provider's spelling, the problems collected for one message with others: what the runtime
     * checks every store with. It keeps its signature (R2-core-03); a new parameter comes through a new method.
     *
     * @param store    the store name, for the problems
     * @param type     the store type
     * @param known    the keys the provider declares
     * @param options  the configured options
     * @param problems where an unknown or repeated key goes
     * @return the options keyed by the declared spelling
     */
    public static Map<String, Object> resolve(String store, String type, Set<String> known, Map<String, Object> options, List<String> problems) {
        Map<String, String> byCanonical = new TreeMap<>();
        for (String key : known) {
            byCanonical.put(canonical(key), key);
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        Map<String, String> given = new TreeMap<>();
        for (Map.Entry<String, Object> option : options.entrySet()) {
            String key = byCanonical.get(canonical(option.getKey()));
            if (key == null) {
                problems.add("store [" + store + "]: unknown option [" + option.getKey() + "] of type [" + type + "]; "
                    + (known.isEmpty() ? "it takes no options" : "known: " + String.join(", ", new TreeSet<>(known))));
                continue;
            }
            String earlier = given.put(key, option.getKey());
            if (earlier != null) {
                String first = earlier.compareTo(option.getKey()) < 0 ? earlier : option.getKey();
                String second = first.equals(earlier) ? option.getKey() : earlier;
                problems.add("store [" + store + "]: options [" + first + "] and [" + second + "] are both " + key);
                continue;
            }
            resolved.put(key, option.getValue());
        }
        return resolved;
    }
}
