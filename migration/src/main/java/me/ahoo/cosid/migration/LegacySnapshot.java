
package me.ahoo.cosid.migration;

import me.ahoo.cosid.migration.source.SourceReader;
import me.ahoo.cosid.migration.source.SourceSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Everything one migration reads from 4.x, across the namespaces it covers (09 §4): the segments of the main namespace
 * and those kept in namespaces of their own ({@code provider.<name>.namespace}, rc-review D-B1), and the machine records
 * of all of them, read from the machine store when 4.x kept machine ids elsewhere (D-B4).
 *
 * @param segments     each segment key to its {@code lastMaxId}
 * @param machines     machine records, {@code {namespace}/{record}} to its raw content
 * @param maxTimestamp T4 over every machine record
 * @param unrecognized keys not migrated, {@code "{key}: {reason}"}
 * @param unmapped     the keys of {@code unrecognized}
 */
record LegacySnapshot(Map<Key, Long> segments, Map<String, String> machines, long maxTimestamp, List<String> unrecognized, Set<String> unmapped) {
    LegacySnapshot {
        segments = Map.copyOf(segments);
        machines = Map.copyOf(machines);
        unrecognized = List.copyOf(unrecognized);
        unmapped = Set.copyOf(unmapped);
    }

    /**
     * A 4.x segment key, {@code {namespace}.{name}}.
     *
     * @param namespace the 4.x namespace
     * @param name      the name after it, with {@code @groupKey} for a grouped segment
     */
    record Key(String namespace, String name) implements Comparable<Key> {
        Key {
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(name, "name");
        }

        @Override
        public int compareTo(Key other) {
            int byNamespace = namespace.compareTo(other.namespace);
            return byNamespace != 0 ? byNamespace : name.compareTo(other.name);
        }

        @Override
        public String toString() {
            return namespace + "." + name;
        }
    }

    /**
     * Reads the namespaces.
     *
     * @param segmentsFrom where the segments are
     * @param machinesFrom where the machine records are; the same reader as {@code segmentsFrom} when 4.x kept both
     *                     in one store
     * @param namespace    the main namespace: every segment in it
     * @param extra        other namespaces and the 4.x segment names to take from them (none: machine records only)
     * @return the snapshot
     */
    static LegacySnapshot read(SourceReader segmentsFrom, SourceReader machinesFrom, String namespace, Map<String, ? extends Set<String>> extra) {
        Set<String> namespaces = new TreeSet<>(extra.keySet());
        namespaces.add(namespace);
        Map<Key, Long> segments = new TreeMap<>();
        Map<String, String> machines = new TreeMap<>();
        List<String> unrecognized = new ArrayList<>();
        Set<String> unmapped = new TreeSet<>();
        long maxTimestamp = 0;
        for (String ns : namespaces) {
            SourceSnapshot snapshot = segmentsFrom.read(ns);
            Set<String> names = ns.equals(namespace) ? null : extra.get(ns);
            snapshot.segments().forEach((name, value) -> {
                // A prefix of another namespace: "shop.v2.order" belongs to namespace "shop.v2", not to "shop" (D-N1).
                if (ownedByAnother(ns, name, namespaces)) {
                    return;
                }
                int at = name.indexOf('@');
                if (names == null || names.contains(at < 0 ? name : name.substring(0, at))) {
                    segments.put(new Key(ns, name), value);
                }
            });
            for (String problem : snapshot.unrecognized()) {
                unrecognized.add(problem);
                int colon = problem.indexOf(": ");
                unmapped.add(colon < 0 ? problem : problem.substring(0, colon));
            }
            SourceSnapshot machineSnapshot = machinesFrom == segmentsFrom ? snapshot : machinesFrom.read(ns);
            machineSnapshot.machines().forEach((id, content) -> machines.put(ns + "/" + id, content));
            maxTimestamp = Math.max(maxTimestamp, machineSnapshot.maxMachineTimestamp());
            if (machinesFrom != segmentsFrom) {
                for (String problem : machineSnapshot.unrecognized()) {
                    if (!snapshot.unrecognized().contains(problem)) {
                        unrecognized.add(problem);
                        int colon = problem.indexOf(": ");
                        unmapped.add(colon < 0 ? problem : problem.substring(0, colon));
                    }
                }
            }
        }
        return new LegacySnapshot(segments, machines, maxTimestamp, unrecognized, unmapped);
    }

    private static boolean ownedByAnother(String namespace, String name, Set<String> namespaces) {
        for (String other : namespaces) {
            if (!other.equals(namespace) && other.startsWith(namespace + ".") && name.startsWith(other.substring(namespace.length() + 1) + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The changes from {@code earlier} to this snapshot, one line each; empty when nothing changed.
     *
     * @param earlier the earlier snapshot
     * @return the changes
     */
    List<String> changesSince(LegacySnapshot earlier) {
        List<String> changes = new ArrayList<>();
        Set<Key> keys = new TreeSet<>(earlier.segments.keySet());
        keys.addAll(segments.keySet());
        for (Key key : keys) {
            if (!Objects.equals(earlier.segments.get(key), segments.get(key))) {
                changes.add("segment [" + key + "]: " + value(earlier.segments.get(key)) + " -> " + value(segments.get(key)));
            }
        }
        Set<String> records = new TreeSet<>(earlier.machines.keySet());
        records.addAll(machines.keySet());
        for (String record : records) {
            if (!Objects.equals(earlier.machines.get(record), machines.get(record))) {
                changes.add("machine record [" + record + "]: " + value(earlier.machines.get(record)) + " -> " + value(machines.get(record)));
            }
        }
        return changes;
    }

    private static String value(Object value) {
        return value == null ? "(none)" : value.toString();
    }
}
