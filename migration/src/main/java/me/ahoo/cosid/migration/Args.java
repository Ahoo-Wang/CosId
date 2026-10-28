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

package me.ahoo.cosid.migration;

import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The options of one command: {@code --name value}, {@code --name=value}, repeatable options and flags.
 */
final class Args {
    private final Map<String, List<String>> values = new LinkedHashMap<>();

    private Args() {
    }

    /**
     * Parses arguments.
     *
     * @param arguments the arguments after the command
     * @param flags     the options that take no value
     * @param known     every option name, flags included
     * @return the options
     * @throws IllegalArgumentException for an unknown option or a missing value
     */
    static Args parse(List<String> arguments, Set<String> flags, Set<String> known) {
        Args args = new Args();
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (!argument.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument [" + argument + "]");
            }
            String name = argument.substring(2);
            String value = null;
            int eq = name.indexOf('=');
            if (eq >= 0) {
                value = name.substring(eq + 1);
                name = name.substring(0, eq);
            }
            if (!known.contains(name)) {
                throw new IllegalArgumentException("Unknown option --" + name);
            }
            if (flags.contains(name)) {
                value = value == null ? "true" : value;
            } else if (value == null) {
                if (i + 1 >= arguments.size()) {
                    throw new IllegalArgumentException("Option --" + name + " needs a value");
                }
                value = arguments.get(++i);
            }
            args.values.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
        return args;
    }

    String get(String name) {
        List<String> list = values.get(name);
        return list == null ? null : list.get(list.size() - 1);
    }

    /**
     * Every value of a repeatable option.
     */
    List<String> all(String name) {
        return values.getOrDefault(name, List.of());
    }

    String required(String name) {
        String value = get(name);
        if (value == null) {
            throw new IllegalArgumentException("Option --" + name + " is required");
        }
        return value;
    }

    boolean flag(String name) {
        return Boolean.parseBoolean(get(name));
    }

    Path path(String name) {
        String value = get(name);
        return value == null ? null : Path.of(value);
    }

    Path path(String name, String fallback) {
        return Path.of(get(name) == null ? fallback : get(name));
    }

    Duration duration(String name, Duration fallback) {
        String value = get(name);
        return value == null ? fallback : parseDuration(value);
    }

    long number(String name, long fallback) {
        String value = get(name);
        return value == null ? fallback : Long.parseLong(value.replace("_", ""));
    }

    /**
     * The {@code key=value} pairs of a repeatable option.
     */
    Map<String, String> pairs(String name) {
        Map<String, String> pairs = new LinkedHashMap<>();
        for (String value : values.getOrDefault(name, List.of())) {
            int eq = value.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("Option --" + name + " takes key=value: " + value);
            }
            pairs.put(value.substring(0, eq), value.substring(eq + 1));
        }
        return pairs;
    }

    /**
     * A duration: ISO-8601 ({@code PT2M}) or a number with {@code ms}, {@code s}, {@code m} or {@code h}; a bare number
     * is milliseconds.
     */
    static Duration parseDuration(String text) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        try {
            if (value.startsWith("pt") || value.startsWith("p")) {
                return Duration.parse(text.trim().toUpperCase(Locale.ROOT));
            }
            if (value.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(value.substring(0, value.length() - 2)));
            }
            if (value.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(value.substring(0, value.length() - 1)));
            }
            return Duration.ofMillis(Long.parseLong(value));
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new IllegalArgumentException("Not a duration: " + text, e);
        }
    }
}
