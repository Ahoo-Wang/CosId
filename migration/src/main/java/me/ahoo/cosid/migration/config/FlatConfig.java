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
 * {@code [i]}. The names of map entries ({@code provider.<name>}, {@code generators.<name>}) are kept as written.
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
                String name = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
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
     * The canonical form of a key: each part kebab-case and lower case, except the names of map entries.
     *
     * @param key the key as written
     * @return the canonical key
     */
    static String canonical(String key) {
        StringBuilder canonical = new StringBuilder(key.length() + 8);
        String parent = "";
        for (String part : key.split("\\.")) {
            String name = MAP_PARENTS.contains(parent) ? part : kebab(part);
            if (!canonical.isEmpty()) {
                canonical.append('.');
            }
            canonical.append(name);
            parent = canonical.toString();
        }
        return canonical.toString();
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
                String rest = key.substring(prefix.length());
                int dot = rest.indexOf('.');
                names.add(dot < 0 ? rest : rest.substring(0, dot));
            }
        }
        return names;
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
