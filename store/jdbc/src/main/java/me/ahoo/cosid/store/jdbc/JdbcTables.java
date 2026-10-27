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

package me.ahoo.cosid.store.jdbc;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The table names of a JDBC store (06-stores.md §2.1): {@code options.rangeTable}, {@code slotTable} and
 * {@code namespaceTable}. Each is an identifier, optionally schema-qualified ({@code cosid.cosid_range}); the store
 * writes them into its SQL as they are, so anything else is rejected.
 *
 * @param range     the table of ranges, {@code cosid_range} by default
 * @param slot      the table of slots, {@code cosid_slot} by default
 * @param namespace the table of namespace metadata, {@code cosid_namespace} by default
 */
public record JdbcTables(String range, String slot, String namespace) {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");
    private static final Pattern DEFAULT_NAMES = Pattern.compile("\\b(cosid_range|cosid_slot|cosid_namespace)\\b");
    /** The default names: {@code cosid_range}, {@code cosid_slot}, {@code cosid_namespace}. */
    public static final JdbcTables DEFAULT = new JdbcTables("cosid_range", "cosid_slot", "cosid_namespace");

    public JdbcTables {
        checked(range, "range");
        checked(slot, "slot");
        checked(namespace, "namespace");
    }

    /**
     * The table names given by store options, the defaults for those not given.
     *
     * @param options the store options: {@code rangeTable}, {@code slotTable}, {@code namespaceTable}
     * @return the table names
     */
    public static JdbcTables of(Map<String, Object> options) {
        return new JdbcTables(option(options, "rangeTable", DEFAULT.range), option(options, "slotTable", DEFAULT.slot),
            option(options, "namespaceTable", DEFAULT.namespace));
    }

    /**
     * Rewrites SQL written for the default names (such as a {@code schema-*.sql} resource) to these names.
     *
     * @param sql the SQL naming {@code cosid_range}, {@code cosid_slot} and {@code cosid_namespace}
     * @return the SQL naming these tables
     */
    public String rename(String sql) {
        Matcher matcher = DEFAULT_NAMES.matcher(sql);
        StringBuilder renamed = new StringBuilder(sql.length());
        while (matcher.find()) {
            String name = switch (matcher.group(1)) {
                case "cosid_range" -> range;
                case "cosid_slot" -> slot;
                default -> namespace;
            };
            matcher.appendReplacement(renamed, Matcher.quoteReplacement(name));
        }
        matcher.appendTail(renamed);
        return renamed.toString();
    }

    private static String option(Map<String, Object> options, String name, String fallback) {
        Object value = options.get(name);
        return value == null ? fallback : value.toString();
    }

    private static void checked(String name, String what) {
        Objects.requireNonNull(name, what);
        if (!IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("The " + what + " table name must be an identifier, optionally schema-qualified: " + name);
        }
    }
}
