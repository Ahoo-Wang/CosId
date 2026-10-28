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

import me.ahoo.cosid.migration.StoreLocation;
import me.ahoo.cosid.migration.Zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The 4.x ZooKeeper store (09 §4.1): a segment is the node {@code /cosid/{ns}.{name}}, an 8-byte big-endian counter
 * (Curator's {@code DistributedAtomicLong}, empty meaning 0) with its lock at {@code /cosid/{ns}.{name}-locker};
 * machine ids live under {@code /cosid/{ns}}: {@code __itc_idx/{instanceId}} and {@code __revert/{machineId}} hold
 * {@code machineId|lastTimestamp}, {@code __counter} and {@code __stable/{instanceId}} the rest.
 */
public final class ZookeeperSource implements SourceReader {
    static final String ROOT = "/cosid";
    private static final String LOCKER = "-locker";
    private final CuratorFramework client;

    public ZookeeperSource(StoreLocation location) {
        this.client = Zookeeper.open(location);
    }

    ZookeeperSource(CuratorFramework client) {
        this.client = client;
    }

    @Override
    public SourceSnapshot read(String namespace) {
        String prefix = namespace + ".";
        Map<String, Long> segments = new TreeMap<>();
        List<String> unrecognized = new ArrayList<>();
        Map<String, String> machines = new TreeMap<>();
        long maxTimestamp = 0;
        int maxMachineId = -1;
        try {
            List<String> children = children(ROOT);
            Set<String> names = new HashSet<>(children);
            for (String child : children) {
                if (!child.startsWith(prefix)) {
                    continue;
                }
                if (child.endsWith(LOCKER) && names.contains(child.substring(0, child.length() - LOCKER.length()))) {
                    continue;
                }
                String path = ROOT + "/" + child;
                Stat stat = new Stat();
                byte[] data = client.getData().storingStatIn(stat).forPath(path);
                if (data != null && data.length == Long.BYTES) {
                    segments.put(child.substring(prefix.length()), ByteBuffer.wrap(data).getLong());
                } else if ((data == null || data.length == 0) && stat.getNumChildren() == 0) {
                    segments.put(child.substring(prefix.length()), 0L);
                } else {
                    unrecognized.add(path + ": not a segment counter (" + (data == null ? 0 : data.length) + " bytes, " + stat.getNumChildren() + " children)");
                }
            }
            String nsPath = ROOT + "/" + namespace;
            for (String node : List.of("__itc_idx", "__revert", "__stable")) {
                for (String child : children(nsPath + "/" + node)) {
                    String state = text(nsPath + "/" + node + "/" + child);
                    machines.put(node + "/" + child, state);
                    if (!"__stable".equals(node)) {
                        maxMachineId = Math.max(maxMachineId, SourceSnapshot.machineIdOf(state));
                    }
                    int bar = state.indexOf('|');
                    if (bar > 0) {
                        try {
                            maxTimestamp = Math.max(maxTimestamp, Long.parseLong(state.substring(bar + 1).trim()));
                        } catch (NumberFormatException e) {
                            unrecognized.add(nsPath + "/" + node + "/" + child + ": unreadable machine state " + state);
                        }
                    }
                }
            }
            if (client.checkExists().forPath(nsPath + "/__counter") != null) {
                byte[] counter = client.getData().forPath(nsPath + "/__counter");
                machines.put("__counter", counter == null ? "" : java.util.HexFormat.of().formatHex(counter));
                // The number of machine ids ever handed out (a Curator atomic integer): the last one is one less.
                if (counter != null && counter.length == Integer.BYTES) {
                    maxMachineId = Math.max(maxMachineId, ByteBuffer.wrap(counter).getInt() - 1);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Reading the 4.x ZooKeeper store failed: " + e.getMessage(), e);
        }
        return new SourceSnapshot(segments, machines, maxTimestamp, unrecognized, maxMachineId);
    }

    private List<String> children(String path) throws Exception {
        try {
            return client.getChildren().forPath(path);
        } catch (KeeperException.NoNodeException e) {
            return List.of();
        }
    }

    private String text(String path) throws Exception {
        byte[] data = client.getData().forPath(path);
        return data == null ? "" : new String(data, StandardCharsets.UTF_8);
    }

    @Override
    public long seal(String namespace, String name, long increment) {
        String path = ROOT + "/" + namespace + "." + name;
        try {
            while (true) {
                Stat stat = new Stat();
                byte[] data;
                try {
                    data = client.getData().storingStatIn(stat).forPath(path);
                } catch (KeeperException.NoNodeException e) {
                    throw new IllegalStateException("Segment [" + path + "] does not exist", e);
                }
                long current = data == null || data.length == 0 ? 0 : ByteBuffer.wrap(data).getLong();
                long sealed = SourceReader.sealed(path, current, increment);
                try {
                    client.setData().withVersion(stat.getVersion()).forPath(path, ByteBuffer.allocate(Long.BYTES).putLong(sealed).array());
                    return sealed;
                } catch (KeeperException.BadVersionException e) {
                    // A 4.x fetch came in between: seal on top of it.
                }
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Sealing segment [" + path + "] failed: " + e.getMessage(), e);
        }
    }

    @Override
    public long now() {
        String path = ROOT + "/.migration-clock-" + java.util.UUID.randomUUID();
        try {
            client.create().creatingParentsIfNeeded().withMode(org.apache.zookeeper.CreateMode.EPHEMERAL).forPath(path);
            Stat stat = client.checkExists().forPath(path);
            client.delete().forPath(path);
            return stat == null ? 0 : stat.getCtime();
        } catch (Exception e) {
            throw new IllegalStateException("Reading the ZooKeeper clock failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
