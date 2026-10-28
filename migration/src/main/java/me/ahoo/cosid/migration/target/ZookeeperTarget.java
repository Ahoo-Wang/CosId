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

package me.ahoo.cosid.migration.target;

import me.ahoo.cosid.migration.StoreLocation;
import me.ahoo.cosid.migration.Zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.transaction.CuratorOp;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Op;
import org.apache.zookeeper.OpResult;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * The 5.0 ZooKeeper store (06 §2.4): a range is the node {@code /cosid/range/{key}}, its data the decimal next value
 * with {@code :sealed} appended once sealed; a namespace is {@code /cosid/slot/{ns}} ({@code limit=..\ntolerance=..\nslotOffset=..})
 * with one node per slot, {@code name=value} lines of which this class only ever raises {@code nextFloor} (and, for a
 * slot whose holder expired, {@code ceilingMin}, the lower bound of the ceiling that becomes the next floor). The other
 * lines are kept byte for byte.
 */
public final class ZookeeperTarget implements TargetAdmin {
    static final String RANGES = "/cosid/range";
    static final String SLOTS = "/cosid/slot";
    private static final String SEALED = ":sealed";
    /** Ops per read-only multi and per transaction. */
    private static final int BATCH = 256;
    private final CuratorFramework client;
    private final boolean owned;

    public ZookeeperTarget(StoreLocation location) {
        this(Zookeeper.open(location), true);
    }

    ZookeeperTarget(CuratorFramework client, boolean owned) {
        this.client = client;
        this.owned = owned;
    }

    @Override
    public long now() {
        String path = "/cosid/.migration-clock-" + UUID.randomUUID();
        return call("read the clock", () -> {
            client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path);
            Stat stat = client.checkExists().forPath(path);
            client.delete().forPath(path);
            return stat.getCtime();
        });
    }

    private static String rangePath(String key) {
        return RANGES + "/" + key;
    }

    @Override
    public Optional<RangeState> range(String key) {
        return call("read range " + key, () -> {
            try {
                return parseRange(key, client.getData().forPath(rangePath(key)));
            } catch (KeeperException.NoNodeException e) {
                return Optional.empty();
            }
        });
    }

    static Optional<RangeState> parseRange(String key, byte[] data) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }
        String text = new String(data, StandardCharsets.US_ASCII);
        boolean sealed = text.endsWith(SEALED);
        long next = Long.parseLong(sealed ? text.substring(0, text.length() - SEALED.length()) : text);
        return Optional.of(new RangeState(key, next, sealed));
    }

    @Override
    public RangeState raise(String key, long next) {
        String path = rangePath(key);
        return call("raise " + key, () -> {
            while (true) {
                Stat stat = new Stat();
                byte[] data;
                try {
                    data = client.getData().storingStatIn(stat).forPath(path);
                } catch (KeeperException.NoNodeException e) {
                    try {
                        client.create().creatingParentsIfNeeded().forPath(path, Long.toString(next).getBytes(StandardCharsets.US_ASCII));
                        return new RangeState(key, next, false);
                    } catch (KeeperException.NodeExistsException exists) {
                        continue;
                    }
                }
                Optional<RangeState> current = parseRange(key, data);
                if (current.isPresent() && current.get().next() >= next) {
                    return current.get();
                }
                boolean sealed = current.isPresent() && current.get().sealed();
                byte[] raised = (next + (sealed ? SEALED : "")).getBytes(StandardCharsets.US_ASCII);
                try {
                    client.setData().withVersion(stat.getVersion()).forPath(path, raised);
                    return new RangeState(key, next, sealed);
                } catch (KeeperException.BadVersionException e) {
                    // changed meanwhile: read again
                }
            }
        });
    }

    @Override
    public List<RangeState> ranges(String prefix) {
        int slash = prefix.lastIndexOf('/');
        String base = slash < 0 ? "" : prefix.substring(0, slash);
        List<RangeState> found = new ArrayList<>();
        call("list ranges", () -> {
            walk(base, prefix, found);
            return null;
        });
        found.sort((a, b) -> a.key().compareTo(b.key()));
        return found;
    }

    private void walk(String key, String prefix, List<RangeState> found) throws Exception {
        String path = key.isEmpty() ? RANGES : rangePath(key);
        List<String> children;
        try {
            if (!key.isEmpty() && key.startsWith(prefix)) {
                parseRange(key, client.getData().forPath(path)).ifPresent(found::add);
            }
            children = client.getChildren().forPath(path);
        } catch (KeeperException.NoNodeException e) {
            return;
        }
        for (String child : children) {
            String childKey = key.isEmpty() ? child : key + "/" + child;
            if (childKey.startsWith(prefix) || prefix.startsWith(childKey + "/")) {
                walk(childKey, prefix, found);
            }
        }
    }

    @Override
    public boolean delete(String key) {
        return call("delete " + key, () -> {
            Stat stat = new Stat();
            byte[] data;
            try {
                data = client.getData().storingStatIn(stat).forPath(rangePath(key));
            } catch (KeeperException.NoNodeException e) {
                return false;
            }
            Optional<RangeState> state = parseRange(key, data);
            if (state.isEmpty() || !state.get().sealed() || stat.getNumChildren() > 0) {
                return false;
            }
            try {
                client.delete().withVersion(stat.getVersion()).forPath(rangePath(key));
                return true;
            } catch (KeeperException.BadVersionException | KeeperException.NotEmptyException e) {
                return false;
            }
        });
    }

    @Override
    public Optional<NamespaceState> namespace(String namespace) {
        return call("read namespace " + namespace, () -> {
            long[] meta = meta(namespace);
            if (meta == null) {
                return Optional.empty();
            }
            int limit = (int) meta[0];
            long now = now();
            int count = 0;
            int live = 0;
            long minNextFloor = Long.MAX_VALUE;
            for (Node node : readAll(slotPaths(namespace, limit))) {
                if (node.data() == null) {
                    continue;
                }
                count++;
                Slot state = Slot.parse(node.data());
                minNextFloor = Math.min(minNextFloor, state.nextFloor(node.stat().getMtime()));
                if (state.held() && state.expiresAt(node.stat().getMtime()) >= now) {
                    live++;
                }
            }
            return Optional.of(new NamespaceState(limit, meta[1], (int) meta[2], count, minNextFloor, live));
        });
    }

    private long[] meta(String namespace) throws Exception {
        byte[] data;
        try {
            data = client.getData().forPath(SLOTS + "/" + namespace);
        } catch (KeeperException.NoNodeException e) {
            return null;
        }
        if (data == null || data.length == 0) {
            return null;
        }
        Map<String, String> fields = Slot.fields(data);
        // Metadata of 5.0.0-alpha.1 has no slotOffset line: offset 0.
        String slotOffset = fields.get("slotOffset");
        return new long[]{Long.parseLong(fields.get("limit")), Long.parseLong(fields.get("tolerance")), slotOffset == null ? 0 : Long.parseLong(slotOffset)};
    }

    @Override
    public NamespaceState prepare(String namespace, int limit, long toleranceMillis, int slotOffset, long nextFloor) {
        String nsPath = SLOTS + "/" + namespace;
        return call("prepare namespace " + namespace, () -> {
            byte[] metaData = ("limit=" + limit + "\ntolerance=" + toleranceMillis + "\nslotOffset=" + slotOffset + "\n").getBytes(StandardCharsets.US_ASCII);
            while (meta(namespace) == null) {
                try {
                    Stat stat = client.checkExists().forPath(nsPath);
                    if (stat == null) {
                        client.create().creatingParentsIfNeeded().forPath(nsPath, metaData);
                    } else {
                        client.setData().withVersion(stat.getVersion()).forPath(nsPath, metaData);
                    }
                } catch (KeeperException.NodeExistsException | KeeperException.BadVersionException e) {
                    // raced: read again
                }
            }
            NamespaceState before = namespace(namespace).orElseThrow();
            if (!TargetAdmin.matches(before, limit, toleranceMillis, slotOffset)) {
                throw TargetAdmin.mismatch(namespace, limit, toleranceMillis, slotOffset, before);
            }
            if (before.live() > 0) {
                throw TargetAdmin.live(namespace, before.live());
            }
            raiseSlots(slotPaths(namespace, limit), nextFloor);
            return namespace(namespace).orElseThrow();
        });
    }

    private static List<String> slotPaths(String namespace, int limit) {
        List<String> paths = new ArrayList<>(limit);
        for (int slot = 0; slot < limit; slot++) {
            paths.add(SLOTS + "/" + namespace + "/" + slot);
        }
        return paths;
    }

    /**
     * Creates the missing slot nodes and raises the others: one batched read, then transactions of versioned writes,
     * read again when one lost a race.
     */
    private void raiseSlots(List<String> paths, long nextFloor) throws Exception {
        for (int attempt = 1; ; attempt++) {
            List<CuratorOp> writes = new ArrayList<>();
            for (Node node : readAll(paths)) {
                if (node.data() == null) {
                    writes.add(client.transactionOp().create().forPath(node.path(), Slot.free(nextFloor)));
                    continue;
                }
                Slot slot = Slot.parse(node.data());
                long mtime = node.stat().getMtime();
                if (slot.nextFloor(mtime) < nextFloor || slot.number("nextFloor") < nextFloor) {
                    writes.add(client.transactionOp().setData().withVersion(node.stat().getVersion()).forPath(node.path(), slot.raised(mtime, nextFloor)));
                }
            }
            try {
                for (int from = 0; from < writes.size(); from += BATCH) {
                    client.transaction().forOperations(writes.subList(from, Math.min(writes.size(), from + BATCH)));
                }
                return;
            } catch (KeeperException.BadVersionException | KeeperException.NodeExistsException e) {
                if (attempt >= 5) {
                    throw e;
                }
            }
        }
    }

    /** Reads nodes with read-only {@code multi} requests of {@value #BATCH} at most; a missing node has null data. */
    private List<Node> readAll(List<String> paths) throws Exception {
        ZooKeeper zooKeeper = client.getZookeeperClient().getZooKeeper();
        List<Node> nodes = new ArrayList<>(paths.size());
        for (int from = 0; from < paths.size(); from += BATCH) {
            List<String> batch = paths.subList(from, Math.min(paths.size(), from + BATCH));
            List<Op> ops = new ArrayList<>(batch.size());
            batch.forEach(path -> ops.add(Op.getData(path)));
            CompletableFuture<List<OpResult>> read = new CompletableFuture<>();
            // A read-only multi is not atomic: it answers every op, a missing node with its own error.
            zooKeeper.multi(ops, (rc, path, ctx, results) -> {
                if (results != null && results.size() == ops.size()) {
                    read.complete(results);
                } else {
                    read.completeExceptionally(KeeperException.create(KeeperException.Code.get(rc)));
                }
            }, null);
            List<OpResult> results = read.get(30, TimeUnit.SECONDS);
            for (int i = 0; i < batch.size(); i++) {
                OpResult result = results.get(i);
                if (result instanceof OpResult.GetDataResult data) {
                    nodes.add(new Node(batch.get(i), data.getData(), data.getStat()));
                } else if (result instanceof OpResult.ErrorResult error && error.getErr() == KeeperException.Code.NONODE.intValue()) {
                    nodes.add(new Node(batch.get(i), null, null));
                } else {
                    throw new IllegalStateException("Reading " + batch.get(i) + " failed: " + result);
                }
            }
        }
        return nodes;
    }

    /** A node as read: null data and stat when it does not exist. */
    private record Node(String path, byte[] data, Stat stat) {
    }

    private <T> T call(String operation, Work<T> work) {
        try {
            return work.run();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(operation + " failed on the 5.0 ZooKeeper store: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        if (owned) {
            client.close();
        }
    }

    @FunctionalInterface
    private interface Work<T> {
        T run() throws Exception;
    }

    /**
     * The {@code name=value} lines of a slot node, in their order, as ISO-8859-1 text so that owner keys round-trip
     * byte for byte.
     */
    record Slot(Map<String, String> lines) {
        static Slot parse(byte[] data) {
            return new Slot(fields(data));
        }

        static Map<String, String> fields(byte[] data) {
            Map<String, String> fields = new LinkedHashMap<>();
            for (String line : new String(data, StandardCharsets.ISO_8859_1).split("\n")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    fields.put(line.substring(0, eq), line.substring(eq + 1));
                }
            }
            return fields;
        }

        /** The data of a free slot never held, as store/zookeeper writes it, with the next floor W. */
        static byte[] free(long nextFloor) {
            return ("fence=0\nttl=0\ntolerance=0\nfloor=0\nexpiresMin=0\nceilingMin=0\nnextFloor=" + nextFloor + "\n").getBytes(StandardCharsets.US_ASCII);
        }

        long number(String name) {
            String value = lines.get(name);
            return value == null ? 0 : Long.parseLong(value);
        }

        boolean held() {
            return lines.containsKey("incarnation");
        }

        long expiresAt(long mtime) {
            return Math.max(number("expiresMin"), mtime + number("ttl"));
        }

        /** {@code SlotState.nextFloor}: the ceiling while held, the recorded value once released. */
        long nextFloor(long mtime) {
            return held() ? Math.max(number("ceilingMin"), expiresAt(mtime) + number("tolerance")) : number("nextFloor");
        }

        /**
         * The data with the next floor raised to {@code nextFloor}. A slot whose holder expired keeps being judged by
         * the {@code mtime} of the node, which this write moves: its {@code ttl} becomes 0 and its expiry and ceiling
         * become lower bounds ({@code expiresMin}, {@code ceilingMin}), so the write neither revives the lease nor
         * lowers its ceiling, and the ceiling, the next holder's floor, is at least {@code nextFloor}.
         */
        byte[] raised(long mtime, long nextFloor) {
            Map<String, String> raised = new LinkedHashMap<>(lines);
            raised.put("nextFloor", Long.toString(Math.max(number("nextFloor"), nextFloor)));
            if (held()) {
                raised.put("expiresMin", Long.toString(expiresAt(mtime)));
                raised.put("ceilingMin", Long.toString(Math.max(nextFloor(mtime), nextFloor)));
                raised.put("ttl", "0");
            }
            StringBuilder text = new StringBuilder();
            raised.forEach((name, value) -> text.append(name).append('=').append(value).append('\n'));
            return text.toString().getBytes(StandardCharsets.ISO_8859_1);
        }
    }
}
