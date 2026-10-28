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

package me.ahoo.cosid.migration.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * What a 4.x store holds for one namespace, as one read sees it (09 §2, §4).
 *
 * @param segments            4.x segment name (after {@code {namespace}.}) to its {@code lastMaxId}, the largest value
 *                            4.x reserved; grouped segments keep their {@code @groupKey}
 * @param machines            machine records, record id to its raw content: compared by the quiet-period check
 * @param maxMachineTimestamp {@code T4}, the largest timestamp of the machine records ({@code last_timestamp},
 *                            {@code watermark}, {@code renew_time} and the like), 0 without records
 * @param unrecognized        keys under the namespace the reader could not interpret, with the reason
 * @param maxMachineId        the largest 4.x machine id the records show ever handed out, -1 without records; the slot
 *                            offset of a namespace that 4.x and 5.0 share during a rolling migration lies above it
 *                            (api-freeze.md §2.7)
 */
public record SourceSnapshot(Map<String, Long> segments, Map<String, String> machines, long maxMachineTimestamp, List<String> unrecognized,
                             int maxMachineId) {
    public SourceSnapshot {
        segments = Map.copyOf(Objects.requireNonNull(segments, "segments"));
        machines = Map.copyOf(Objects.requireNonNull(machines, "machines"));
        unrecognized = List.copyOf(Objects.requireNonNull(unrecognized, "unrecognized"));
    }

    /**
     * A snapshot without machine ids.
     *
     * @param segments            the segments
     * @param machines            the machine records
     * @param maxMachineTimestamp T4
     * @param unrecognized        the keys not interpreted
     */
    public SourceSnapshot(Map<String, Long> segments, Map<String, String> machines, long maxMachineTimestamp, List<String> unrecognized) {
        this(segments, machines, maxMachineTimestamp, unrecognized, -1);
    }

    /**
     * The machine id of a 4.x state {@code machineId|lastTimestamp}.
     *
     * @param state the state
     * @return the machine id, -1 when unreadable
     */
    public static int machineIdOf(String state) {
        if (state == null) {
            return -1;
        }
        int bar = state.indexOf('|');
        try {
            return Integer.parseInt((bar < 0 ? state : state.substring(0, bar)).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * The changes from {@code earlier} to this snapshot, one line each; empty when nothing changed.
     *
     * @param earlier the earlier snapshot
     * @return the changes
     */
    public List<String> changesSince(SourceSnapshot earlier) {
        List<String> changes = new ArrayList<>();
        diff("segment", earlier.segments, segments, changes);
        diff("machine record", earlier.machines, machines, changes);
        return changes;
    }

    private static <V> void diff(String what, Map<String, V> before, Map<String, V> after, List<String> changes) {
        TreeSet<String> keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            V old = before.get(key);
            V now = after.get(key);
            if (!Objects.equals(old, now)) {
                changes.add(what + " [" + key + "]: " + (old == null ? "(none)" : old) + " -> " + (now == null ? "(none)" : now));
            }
        }
    }

    /**
     * The segments in name order.
     *
     * @return the segments
     */
    public TreeMap<String, Long> sortedSegments() {
        return new TreeMap<>(segments);
    }
}
