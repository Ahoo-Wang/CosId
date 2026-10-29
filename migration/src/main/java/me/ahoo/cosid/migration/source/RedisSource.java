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

import me.ahoo.cosid.migration.RedisConnection;
import me.ahoo.cosid.migration.StoreLocation;

import io.lettuce.core.cluster.api.sync.RedisClusterCommands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The 4.x Redis store (09 §4.1, {@code cosid-spring-redis}): a segment is the string {@code cosid:{ns.name}.adder};
 * machine ids live in {@code cosid:{ns}:itc_idx} ({@code instanceId -> "machineId|timestamp"}), {@code :revert}
 * ({@code machineId -> timestamp}), {@code :adder}, {@code :stable} and {@code :lease}
 * ({@code machineId -> "renewedAt|watermark|version|timestamp"}).
 */
public final class RedisSource implements SourceReader {
    private static final String SUFFIX = "}.adder";
    private final RedisConnection redis;

    public RedisSource(StoreLocation location) {
        this(RedisConnection.open(location));
    }

    /**
     * A reader on a connection of its own.
     *
     * @param redis the connection, closed with the reader
     */
    public RedisSource(RedisConnection redis) {
        this.redis = redis;
    }

    static String segmentKey(String namespace, String name) {
        return "cosid:{" + namespace + "." + name + SUFFIX;
    }

    @Override
    public SourceSnapshot read(String namespace) {
        RedisClusterCommands<String, String> commands = redis.commands();
        String prefix = "cosid:{" + namespace + ".";
        Map<String, Long> segments = new TreeMap<>();
        List<String> unrecognized = new ArrayList<>();
        for (String key : redis.scan(RedisConnection.globEscape(prefix) + "*" + RedisConnection.globEscape(SUFFIX))) {
            String name = key.substring(prefix.length(), key.length() - SUFFIX.length());
            String value = commands.get(key);
            try {
                segments.put(name, Long.parseLong(value));
            } catch (NumberFormatException e) {
                unrecognized.add(key + ": not a counter (" + value + ")");
            }
        }
        Map<String, String> machines = new TreeMap<>();
        long maxTimestamp = 0;
        int maxMachineId = -1;
        String machinePrefix = "cosid:{" + namespace + "}:";
        for (Map.Entry<String, String> entry : hash(commands, machinePrefix + "itc_idx").entrySet()) {
            machines.put("itc_idx/" + entry.getKey(), entry.getValue());
            maxMachineId = Math.max(maxMachineId, SourceSnapshot.machineIdOf(entry.getValue()));
            maxTimestamp = Math.max(maxTimestamp, numbers(entry.getValue(), 1));
        }
        for (Map.Entry<String, String> entry : hash(commands, machinePrefix + "revert").entrySet()) {
            machines.put("revert/" + entry.getKey(), entry.getValue());
            maxMachineId = Math.max(maxMachineId, SourceSnapshot.machineIdOf(entry.getKey()));
            maxTimestamp = Math.max(maxTimestamp, numbers(entry.getValue(), 0));
        }
        for (Map.Entry<String, String> entry : hash(commands, machinePrefix + "lease").entrySet()) {
            machines.put("lease/" + entry.getKey(), entry.getValue());
            maxMachineId = Math.max(maxMachineId, SourceSnapshot.machineIdOf(entry.getKey()));
            maxTimestamp = Math.max(maxTimestamp, numbers(entry.getValue(), 0));
        }
        String adder = commands.get(machinePrefix + "adder");
        if (adder != null) {
            machines.put("adder", adder);
            // The last machine id ever handed out.
            maxMachineId = Math.max(maxMachineId, SourceSnapshot.machineIdOf(adder));
        }
        for (String stable : commands.smembers(machinePrefix + "stable")) {
            machines.put("stable/" + stable, "1");
        }
        return new SourceSnapshot(segments, machines, maxTimestamp, unrecognized, maxMachineId);
    }

    private static Map<String, String> hash(RedisClusterCommands<String, String> commands, String key) {
        Map<String, String> entries = commands.hgetall(key);
        return entries == null ? Map.of() : entries;
    }

    /**
     * The largest of the {@code |}-separated numbers of a value from index {@code from} on; the lease entries keep
     * their version (a counter) among timestamps, which is far smaller than any of them.
     */
    static long numbers(String value, int from) {
        long max = 0;
        String[] parts = value.split("\\|");
        for (int i = from; i < parts.length; i++) {
            try {
                max = Math.max(max, Long.parseLong(parts[i].trim()));
            } catch (NumberFormatException e) {
                // not a number: not a timestamp either
            }
        }
        return max;
    }

    @Override
    public long seal(String namespace, String name, long increment) {
        String key = segmentKey(namespace, name);
        // On the connection WAIT then counts (R2-mig-03).
        RedisClusterCommands<String, String> commands = redis.commandsFor(key);
        String value = commands.get(key);
        if (value == null) {
            throw new IllegalStateException("Segment [" + key + "] does not exist");
        }
        SourceReader.sealed(key, Long.parseLong(value), increment);
        long sealed = commands.incrby(key, increment);
        redis.replicated(key);
        return sealed;
    }

    @Override
    public long now() {
        List<String> time = redis.commands().time();
        return Long.parseLong(time.get(0)) * 1000 + Long.parseLong(time.get(1)) / 1000;
    }

    @Override
    public void close() {
        redis.close();
    }
}
