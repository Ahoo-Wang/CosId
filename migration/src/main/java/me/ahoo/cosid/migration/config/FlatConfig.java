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

package me.ahoo.cosid.migration.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A Spring configuration file (YAML or {@code .properties}) as flat keys in Spring's canonical form: lower case,
 * kebab-case ({@code machineBit}, {@code machine_bit} and {@code machine-bit} are the same key), list items as
 * {@code [i]}. The names of map entries ({@code provider.<name>}, {@code generators.<name>}) are kept as written; a
 * dotted name stays one segment, bracketed ({@code provider.[orders.Order]}), so it survives the dot split.
 * Several YAML documents are merged in order; profiles are not evaluated.
 */
public final class FlatConfig {
    private static final ObjectMapper YAML = new YAMLMapper(new YAMLFactory());
    private static final Set<String> MAP_PARENTS = Set.of("cosid.segment.provider", "cosid.snowflake.provider", "cosid.generators", "cosid.stores");
    private final Map<String, String> values;

    FlatConfig(Map<String, String> values) {
        this.values = new TreeMap<>(values);
    }

    /**
     * Loads a file: {@code .properties} as properties, anything else as YAML.
     *
     * @param file the file
     * @return the configuration
     */
    public static FlatConfig load(Path file) {
        Map<String, String> values = new TreeMap<>();
        try {
            if (file.toString().endsWith(".properties")) {
                Properties properties = new Properties();
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
                for (String name : properties.stringPropertyNames()) {
                    values.put(canonical(name), properties.getProperty(name).trim());
                }
            } else {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    var documents = YAML.readerFor(JsonNode.class).readValues(reader);
                    while (documents.hasNext()) {
                        flatten("", (JsonNode) documents.next(), values);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Reading " + file + " failed: " + e.getMessage(), e);
        }
        return new FlatConfig(values);
    }

    /**
     * Parses YAML text.
     *
     * @param yaml the text
     * @return the configuration
     */
    public static FlatConfig ofYaml(String yaml) {
        Map<String, String> values = new TreeMap<>();
        try {
            var documents = YAML.readerFor(JsonNode.class).readValues(yaml);
            while (documents.hasNext()) {
                flatten("", (JsonNode) documents.next(), values);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new FlatConfig(values);
    }

    private static void flatten(String prefix, JsonNode node, Map<String, String> values) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            if (node.isEmpty() && !prefix.isEmpty()) {
                // "provider: { order: {} }" declares an entry with every default: keep its name.
                values.put(canonical(prefix), "");
            }
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String key = field.getKey();
                // A YAML field key is atomic: a dotted key under a map is one entry name, not nesting, so bracket
                // it before the dot join, or `orders.Order` would flatten to the nested `orders.order`. The
                // prefix is the raw YAML path, so compare case-insensitively: `Segment:`/`Provider:` bind the
                // same map under Spring's relaxed binding.
                if (key.indexOf('.') >= 0 && MAP_PARENTS.contains(prefix.toLowerCase(Locale.ROOT)) && !key.startsWith("[")) {
                    key = "[" + key + "]";
                }
                String name = prefix.isEmpty() ? key : prefix + "." + key;
                flatten(name, field.getValue(), values);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flatten(prefix + "[" + i + "]", node.get(i), values);
            }
        } else {
            values.put(canonical(prefix), node.asText().trim());
        }
    }

    /**
     * The canonical form of a key: each part kebab-case and lower case, except the names of map entries. A bracketed
     * name that carries a dot stays bracketed and as written ({@code provider.[orders.Order]}); any other bracketed
     * name loses its brackets ({@code provider.[order]} becomes {@code provider.order}), and a list index stays
     * attached ({@code foo[0]}). A malformed bracket (unclosed, or empty) keeps its text, attached to the previous
     * part, instead of losing a character or producing a double dot. Without brackets a
     * dotted {@code .properties} key is nested maps, not a dotted name.
     *
     * @param key the key as written
     * @return the canonical key
     */
    static String canonical(String key) {
        StringBuilder canonical = new StringBuilder(key.length() + 8);
        String parent = "";
        for (String part : split(key)) {
            if (part.startsWith("[")) {
                if (!part.endsWith("]")) {
                    // Unclosed bracket: malformed input, keep the text as written instead of dropping its last
                    // character.
                    canonical.append(part);
                    parent = canonical.toString();
                    continue;
                }
                String inner = part.substring(1, part.length() - 1);
                if (inner.indexOf('.') >= 0) {
                    if (!canonical.isEmpty()) {
                        canonical.append('.');
                    }
                    canonical.append(part);
                } else if (inner.isEmpty() || inner.chars().allMatch(Character::isDigit)) {
                    // A list index, or empty brackets: attached to the previous part, as written.
                    canonical.append(part);
                } else {
                    if (!canonical.isEmpty()) {
                        canonical.append('.');
                    }
                    canonical.append(MAP_PARENTS.contains(parent) ? inner : kebab(inner));
                }
            } else {
                String name = MAP_PARENTS.contains(parent) ? part : kebab(part);
                if (!canonical.isEmpty()) {
                    canonical.append('.');
                }
                canonical.append(name);
            }
            parent = canonical.toString();
        }
        return canonical.toString();
    }

    /**
     * Splits a key into segments on dots outside brackets: a plain run up to a dot or a bracket, or a bracketed group
     * ({@code [orders.Order]}, {@code [0]}) as one segment.
     */
    private static List<String> split(String key) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < key.length()) {
            char c = key.charAt(i);
            if (c == '.') {
                i++;
            } else if (c == '[') {
                int close = key.indexOf(']', i);
                if (close < 0) {
                    parts.add(key.substring(i));
                    break;
                }
                parts.add(key.substring(i, close + 1));
                i = close + 1;
            } else {
                int end = i;
                while (end < key.length() && key.charAt(end) != '.' && key.charAt(end) != '[') {
                    end++;
                }
                parts.add(key.substring(i, end));
                i = end;
            }
        }
        return parts;
    }

    private static String kebab(String part) {
        StringBuilder kebab = new StringBuilder(part.length() + 4);
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0 && kebab.charAt(kebab.length() - 1) != '-') {
                    kebab.append('-');
                }
                kebab.append(Character.toLowerCase(c));
            } else if (c == '_') {
                kebab.append('-');
            } else {
                kebab.append(c);
            }
        }
        return kebab.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * A value.
     *
     * @param key the canonical key
     * @return the value, or null
     */
    public String get(String key) {
        return values.get(key);
    }

    /**
     * A value or a default.
     *
     * @param key      the canonical key
     * @param fallback the default
     * @return the value
     */
    public String get(String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isEmpty() ? fallback : value;
    }

    public boolean bool(String key, boolean fallback) {
        String value = values.get(key);
        return value == null || value.isEmpty() ? fallback : Boolean.parseBoolean(value);
    }

    public long number(String key, long fallback) {
        String value = values.get(key);
        return value == null || value.isEmpty() ? fallback : Long.parseLong(value);
    }

    /**
     * The names of the entries of a map.
     *
     * @param parent the canonical key of the map
     * @return the entry names
     */
    public Set<String> names(String parent) {
        Set<String> names = new TreeSet<>();
        String prefix = parent + ".";
        for (String key : values.keySet()) {
            if (key.startsWith(prefix)) {
                names.add(firstSegment(key.substring(prefix.length())));
            }
        }
        return names;
    }

    /** The first segment of a canonical key suffix: a bracketed name without its brackets, or up to the next dot. */
    private static String firstSegment(String rest) {
        if (rest.startsWith("[")) {
            int close = rest.indexOf(']');
            return close < 0 ? rest.substring(1) : rest.substring(1, close);
        }
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    /**
     * The canonical key prefix of a map entry: bracketed when the name carries a dot, so the name stays one segment.
     *
     * @param parent the canonical key of the map
     * @param name   the entry name
     * @return the canonical key prefix
     */
    static String childKey(String parent, String name) {
        return name.indexOf('.') >= 0 ? parent + ".[" + name + "]" : parent + "." + name;
    }

    /**
     * The keys under a prefix.
     *
     * @param prefix the canonical prefix, such as {@code cosid.machine.guarder}
     * @return the keys
     */
    public List<String> keys(String prefix) {
        return values.keySet().stream().filter(key -> key.equals(prefix) || key.startsWith(prefix + ".") || key.startsWith(prefix + "[")).toList();
    }

    /**
     * All keys.
     *
     * @return the keys
     */
    public Set<String> keys() {
        return values.keySet();
    }
}
