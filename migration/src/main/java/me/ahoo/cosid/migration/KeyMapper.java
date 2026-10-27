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

import me.ahoo.cosid.migration.config.Legacy4x;
import me.ahoo.cosid.segment.Period;

import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Maps 4.x segment names to 5.0 range keys (09 §4.1): {@code {name}} to {@code {namespace}/{name}}, and a grouped
 * {@code {name}@{groupKey}} to {@code {namespace}/{name}/{period}}. A group key must be a 5.0 period preset
 * ({@code yyyy}, {@code yyyyMM}, {@code yyyyMMdd}), or the operator names the 4.x pattern it was formatted with
 * ({@code --group-pattern name=pattern}, or {@code group.pattern} of {@code --layout-config}), from which it is
 * reformatted to the preset of the same granularity.
 */
public final class KeyMapper {
    private static final Map<String, Period> PRESETS = Map.of("YEAR", Period.YEAR, "YEAR_MONTH", Period.YEAR_MONTH, "YEAR_MONTH_DAY", Period.YEAR_MONTH_DAY);
    private final String namespace;
    private final Map<String, String> renames;
    private final Map<String, String> groupPatterns;

    /**
     * A mapper.
     *
     * @param namespace     the namespace
     * @param renames       4.x name to 5.0 generator name; {@code __share__} maps to {@code default} unless named here
     * @param groupPatterns 5.0 generator name to the 4.x pattern of its group keys
     */
    public KeyMapper(String namespace, Map<String, String> renames, Map<String, String> groupPatterns) {
        this.namespace = Objects.requireNonNull(namespace, "namespace");
        Map<String, String> withShare = new HashMap<>();
        withShare.put(Legacy4x.SHARE, Legacy4x.DEFAULT);
        withShare.putAll(renames);
        this.renames = Map.copyOf(withShare);
        this.groupPatterns = Map.copyOf(groupPatterns);
    }

    /**
     * Maps one 4.x name.
     *
     * @param sourceName the 4.x name after {@code {namespace}.}
     * @return the mapping
     */
    public Mapping map(String sourceName) {
        int at = sourceName.indexOf('@');
        String base = at < 0 ? sourceName : sourceName.substring(0, at);
        String generator = renames.getOrDefault(base, base);
        if (generator.isEmpty() || generator.contains("/")) {
            return Mapping.unrecognized(sourceName, "5.0 generator names must be non-empty and contain no '/': [" + generator + "]; use --rename");
        }
        if (at < 0) {
            return new Mapping(sourceName, generator, null, null, namespace + "/" + generator, null);
        }
        String groupKey = sourceName.substring(at + 1);
        String pattern = groupPatterns.containsKey(generator) ? groupPatterns.get(generator) : groupPatterns.get(base);
        try {
            String[] period = pattern == null ? preset(groupKey) : reformat(groupKey, pattern);
            return new Mapping(sourceName, generator, period[0], period[1], namespace + "/" + generator + "/" + period[1], null);
        } catch (IllegalArgumentException | DateTimeException e) {
            return Mapping.unrecognized(sourceName, "group key [" + groupKey + "] " + e.getMessage()
                + "; name its 4.x pattern with --group-pattern " + generator + "=<pattern>");
        }
    }

    /** The preset whose strict format the group key has: by length, 4, 6 or 8 digits. */
    static String[] preset(String groupKey) {
        String name = switch (groupKey.length()) {
            case 4 -> "YEAR";
            case 6 -> "YEAR_MONTH";
            case 8 -> "YEAR_MONTH_DAY";
            default -> throw new IllegalArgumentException("is not a 5.0 period preset (yyyy, yyyyMM, yyyyMMdd)");
        };
        // endOf parses strictly: "20261332" is refused, not adjusted.
        PRESETS.get(name).endOf(groupKey, ZoneOffset.UTC);
        return new String[]{name, groupKey};
    }

    /** Parses a group key with its 4.x pattern and formats it as the preset of the pattern's granularity. */
    static String[] reformat(String groupKey, String pattern) {
        TemporalAccessor parsed = DateTimeFormatter.ofPattern(pattern, Locale.ROOT).parse(groupKey);
        int year = parsed.get(ChronoField.YEAR);
        if (pattern.contains("d")) {
            return preset(String.format(Locale.ROOT, "%04d%02d%02d", year, parsed.get(ChronoField.MONTH_OF_YEAR), parsed.get(ChronoField.DAY_OF_MONTH)));
        }
        if (pattern.contains("M")) {
            return preset(String.format(Locale.ROOT, "%04d%02d", year, parsed.get(ChronoField.MONTH_OF_YEAR)));
        }
        return preset(String.format(Locale.ROOT, "%04d", year));
    }

    /**
     * The 5.0 key of one 4.x name.
     *
     * @param sourceName the 4.x name
     * @param generator  the 5.0 generator name
     * @param period     the period preset of a grouped segment, null otherwise
     * @param periodKey  the 5.0 period key, null otherwise
     * @param targetKey  the 5.0 range key, null when not migrated
     * @param problem    why it is not migrated, null otherwise
     */
    public record Mapping(String sourceName, String generator, String period, String periodKey, String targetKey, String problem) {
        static Mapping unrecognized(String sourceName, String problem) {
            return new Mapping(sourceName, null, null, null, null, problem);
        }

        public boolean mapped() {
            return problem == null;
        }
    }
}
