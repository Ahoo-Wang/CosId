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

package me.ahoo.cosid.store.zookeeper;

import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.NamespaceMismatchException;
import me.ahoo.cosid.lease.AcquireReason;
import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.lease.SlotLease;
import me.ahoo.cosid.lease.SlotLeaseStore;

import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Op;
import org.apache.zookeeper.OpResult;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.common.PathUtils;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link SlotLeaseStore} on ZooKeeper (06-stores.md §2.4).
 *
 * <ul>
 *     <li>{@code /cosid/slot/{ns}}: the namespace metadata {@code limit} and {@code tolerance}, recorded on first use;</li>
 *     <li>{@code /cosid/slot/{ns}/{slot}}: a slot, {@link SlotState}; created by its first acquire, never deleted;</li>
 *     <li>{@code /cosid/slot/{ns}/.clock}: written by every acquire, its {@code mtime} is the {@code now} of the
 *     expiry check.</li>
 * </ul>
 *
 * <p>Every state change (05 §2.2) is a compare-and-set of one slot node: a {@code setData} at the version read, or
 * the {@code create} of a slot never used. Times are the {@code mtime} of the write, stamped by the ZooKeeper leader;
 * {@code storeNow} and {@code expiresAt} come from the same write. Uniqueness depends on none of them (05 §3): each
 * holder's floor is the ceiling the previous write left in the node it replaces. Acquire reads every slot of the
 * namespace with one batch of asynchronous {@code getData}, then tries the slots in the order of 05 §2.1.
 */
final class ZookeeperSlotLeaseStore implements SlotLeaseStore {
    static final String ROOT = "/cosid/slot";
    static final String CLOCK = ".clock";
    static final int READ_BATCH = 256;

    private final Zk zk;
    /** The namespaces whose metadata this instance checked: the metadata never changes once recorded. */
    private final Map<String, Metadata> namespaces = new ConcurrentHashMap<>();

    ZookeeperSlotLeaseStore(Zk zk) {
        this.zk = Objects.requireNonNull(zk, "zk");
    }

    static String namespacePath(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        if (namespace.indexOf('/') >= 0) {
            throw new IllegalArgumentException("Namespace [" + namespace + "] must not contain '/'");
        }
        String path = ROOT + "/" + namespace;
        try {
            PathUtils.validatePath(path);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Namespace [" + namespace + "] is no valid ZooKeeper node name: " + e.getMessage(), e);
        }
        return path;
    }

    static String slotPath(String namespace, int slot) {
        return namespacePath(namespace) + "/" + slot;
    }

    @Override
    public AcquireResult acquire(String namespace, Owner owner, int limit, Duration ttl, Duration tolerance, OptionalInt preferredSlot) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(preferredSlot, "preferredSlot");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        long ttlMillis = positiveMillis(ttl, "ttl");
        long toleranceMillis = toleranceMillis(tolerance);
        String nsPath = namespacePath(namespace);
        String what = "acquire " + nsPath;
        long deadline = zk.deadline();
        checkMetadata(namespace, nsPath, new Metadata(limit, toleranceMillis), deadline);
        for (int attempt = 0; ; attempt++) {
            // The clock write and the reads go out together; ZooKeeper answers them in order, one round trip in all.
            CompletableFuture<CuratorEvent> clock = touchClock(nsPath);
            CompletableFuture<CuratorEvent> children = zk.start(callback -> zk.client().getChildren().inBackground(callback).forPath(nsPath));
            long now = clockNow(nsPath, clock, deadline);
            int[] used = usedSlots(what, zk.await(what, deadline, children), limit);
            Node[] nodes = nodes(what, nsPath, limit, used, readSlots(what, nsPath, used), deadline);
            if (nodes == null) {
                Zk.backoff(attempt, deadline, what);
                continue;
            }
            Optional<AcquireResult> result = choose(namespace, owner, nodes, now, ttlMillis, toleranceMillis, preferredSlot, deadline);
            if (result.isPresent()) {
                return result.get();
            }
            Zk.backoff(attempt, deadline, what);
        }
    }

    /**
     * The slots below the limit that have a node, in order; the others were never used.
     */
    private static int[] usedSlots(String what, CuratorEvent children, int limit) {
        if (Zk.code(children) != KeeperException.Code.OK) {
            throw Zk.unexpected(what, children);
        }
        int[] used = new int[Math.min(limit, children.getChildren().size())];
        int count = 0;
        for (String child : children.getChildren()) {
            int slot = slotOf(child);
            if (slot >= 0 && slot < limit && count < used.length) {
                used[count++] = slot;
            }
        }
        int[] sorted = Arrays.copyOf(used, count);
        Arrays.sort(sorted);
        return sorted;
    }

    /** The slot a child node names, or -1 for another node such as the clock. */
    private static int slotOf(String child) {
        if (child.isEmpty() || child.length() > 9) {
            return -1;
        }
        int slot = 0;
        for (int i = 0; i < child.length(); i++) {
            char c = child.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            slot = slot * 10 + (c - '0');
        }
        return slot;
    }

    /**
     * Reads the used slots of the namespace: read-only {@code multi} requests of {@value #READ_BATCH} {@code getData}
     * each, on the ZooKeeper handle of the client. One request per batch keeps the allocation and the round trips of a
     * 1024-slot namespace low, where a Curator background call per slot does not.
     */
    private List<CompletableFuture<List<OpResult>>> readSlots(String what, String nsPath, int[] used) {
        ZooKeeper zooKeeper;
        try {
            zooKeeper = zk.client().getZookeeperClient().getZooKeeper();
        } catch (Exception e) {
            throw new CosIdException("ZooKeeper " + what + ": no connection", e);
        }
        String clientNamespace = zk.client().getNamespace();
        List<CompletableFuture<List<OpResult>>> reads = new ArrayList<>(used.length / READ_BATCH + 1);
        for (int from = 0; from < used.length; from += READ_BATCH) {
            List<Op> ops = new ArrayList<>(Math.min(READ_BATCH, used.length - from));
            for (int i = from; i < Math.min(used.length, from + READ_BATCH); i++) {
                ops.add(Op.getData(ZKPaths.fixForNamespace(clientNamespace, nsPath + "/" + used[i])));
            }
            CompletableFuture<List<OpResult>> read = new CompletableFuture<>();
            zooKeeper.multi(ops, (rc, path, ctx, results) -> {
                // A read-only multi is not atomic: it answers every op, the code being that of the first failed one.
                if (results != null && results.size() == ops.size()) {
                    read.complete(results);
                } else {
                    read.completeExceptionally(KeeperException.create(KeeperException.Code.get(rc)));
                }
            }, null);
            reads.add(read);
        }
        return reads;
    }

    /**
     * The slots as read; a slot without a node is {@link SlotState#NEW}.
     *
     * @return the nodes, or null if the connection was lost and the reads must be tried again
     */
    private Node[] nodes(String what, String nsPath, int limit, int[] used, List<CompletableFuture<List<OpResult>>> reads, long deadline) {
        Node[] nodes = new Node[limit];
        int index = 0;
        for (CompletableFuture<List<OpResult>> read : reads) {
            List<OpResult> results;
            try {
                results = zk.await(what, deadline, read);
            } catch (CosIdException e) {
                if (e.getCause() instanceof KeeperException.ConnectionLossException) {
                    return null;
                }
                throw e;
            }
            for (OpResult result : results) {
                int slot = used[index++];
                String path = nsPath + "/" + slot;
                if (result instanceof OpResult.GetDataResult data) {
                    nodes[slot] = new Node(path, SlotState.parse(path, data.getData()), data.getStat(), data.getStat().getMtime());
                } else if (result instanceof OpResult.ErrorResult error) {
                    throw new CosIdException("ZooKeeper " + what + " failed reading " + path + ": " + KeeperException.Code.get(error.getErr()));
                } else {
                    throw new CosIdException("ZooKeeper " + what + " returned " + result + " for " + path);
                }
            }
        }
        for (int slot = 0; slot < limit; slot++) {
            if (nodes[slot] == null) {
                nodes[slot] = new Node(nsPath + "/" + slot, SlotState.NEW, null, 0);
            }
        }
        return nodes;
    }

    /**
     * One pass of the slot choice (05 §2.1) over the nodes as read.
     *
     * @return the result, or empty if a compare-and-set lost a race and the nodes must be read again
     */
    private Optional<AcquireResult> choose(String namespace, Owner owner, Node[] nodes, long now, long ttlMillis, long toleranceMillis,
                                           OptionalInt preferredSlot, long deadline) {
        boolean duplicate = false;
        for (Node node : nodes) {
            SlotState state = node.state;
            duplicate |= !node.free(now) && state.ownerKey().equals(owner.key()) && !state.incarnation().equals(owner.incarnation());
        }
        for (int slot = 0; slot < nodes.length; slot++) {
            if (owner.equals(nodes[slot].state.owner())) {
                return Optional.of(new AcquireResult.Acquired(nodes[slot].state.toLease(namespace, slot, nodes[slot].mtime, now),
                    AcquireReason.IDEMPOTENT, duplicate));
            }
        }
        if (preferredSlot.isPresent()) {
            int preferred = preferredSlot.getAsInt();
            if (preferred >= 0 && preferred < nodes.length && nodes[preferred].free(now)) {
                return take(namespace, preferred, nodes[preferred], owner, ttlMillis, toleranceMillis, AcquireReason.PREFERRED, duplicate, deadline);
            }
        }
        for (int slot = 0; slot < nodes.length; slot++) {
            if (owner.key().equals(nodes[slot].state.lastOwnerKey()) && nodes[slot].free(now)) {
                return take(namespace, slot, nodes[slot], owner, ttlMillis, toleranceMillis, AcquireReason.STICKY, duplicate, deadline);
            }
        }
        long earliestExpiresAt = Long.MAX_VALUE;
        for (int slot = 0; slot < nodes.length; slot++) {
            if (nodes[slot].free(now)) {
                return take(namespace, slot, nodes[slot], owner, ttlMillis, toleranceMillis, AcquireReason.LOWEST, duplicate, deadline);
            }
            earliestExpiresAt = Math.min(earliestExpiresAt, nodes[slot].state.expiresAt(nodes[slot].mtime));
        }
        return Optional.of(new AcquireResult.Exhausted(earliestExpiresAt));
    }

    private Optional<AcquireResult> take(String namespace, int slot, Node node, Owner owner, long ttlMillis, long toleranceMillis,
                                         AcquireReason reason, boolean duplicate, long deadline) {
        String path = node.path;
        String what = "acquire " + path;
        SlotState taken = node.state.take(owner, node.mtime, ttlMillis, toleranceMillis);
        byte[] data = taken.bytes();
        Stat stat;
        if (node.stat == null) {
            Stat created = new Stat();
            CuratorEvent create = zk.call(what, deadline, callback -> zk.client().create().storingStatIn(created).withMode(CreateMode.PERSISTENT)
                .inBackground(callback).forPath(path, data));
            switch (Zk.code(create)) {
                case OK -> stat = create.getStat() != null ? create.getStat() : created;
                case NODEEXISTS -> {
                    return Optional.empty();
                }
                default -> throw Zk.unexpected(what, create);
            }
        } else {
            Optional<Stat> written = write(what, node, taken, deadline);
            if (written.isEmpty()) {
                return Optional.empty();
            }
            stat = written.get();
        }
        long mtime = stat.getMtime();
        return Optional.of(new AcquireResult.Acquired(taken.toLease(namespace, slot, mtime, mtime), reason, duplicate));
    }

    @Override
    public Optional<SlotLease> renew(SlotLease lease, Duration ttl, Duration tolerance) {
        Objects.requireNonNull(lease, "lease");
        long ttlMillis = positiveMillis(ttl, "ttl");
        long toleranceMillis = toleranceMillis(tolerance);
        String path = slotPath(lease.namespace(), lease.slot());
        String what = "renew " + path;
        long deadline = zk.deadline();
        for (int attempt = 0; ; attempt++) {
            Optional<Node> node = read(what, path, deadline);
            if (node.isEmpty() || !node.get().state.heldBy(lease)) {
                return Optional.empty();
            }
            SlotState renewed = node.get().state.renew(node.get().mtime, ttlMillis, toleranceMillis);
            Optional<Stat> written = write(what, node.get(), renewed, deadline);
            if (written.isPresent()) {
                long mtime = written.get().getMtime();
                return Optional.of(renewed.toLease(lease.namespace(), lease.slot(), mtime, mtime));
            }
            Zk.backoff(attempt, deadline, what);
        }
    }

    @Override
    public boolean release(SlotLease lease, long lastIssued) {
        Objects.requireNonNull(lease, "lease");
        String path = slotPath(lease.namespace(), lease.slot());
        String what = "release " + path;
        long deadline = zk.deadline();
        boolean conflicted = false;
        for (int attempt = 0; ; attempt++) {
            Optional<Node> node = read(what, path, deadline);
            if (node.isEmpty()) {
                return false;
            }
            SlotState state = node.get().state;
            if (!state.heldBy(lease)) {
                // A lost race may be our own write, applied though its response was lost and Curator retried it: only the
                // release of this very tenure leaves the slot free at the next fence.
                return conflicted && !state.held() && state.fence() == lease.fence() + 1 && lease.owner().key().equals(state.lastOwnerKey());
            }
            if (write(what, node.get(), state.release(node.get().mtime, lastIssued), deadline).isPresent()) {
                return true;
            }
            conflicted = true;
            Zk.backoff(attempt, deadline, what);
        }
    }

    private Optional<Node> read(String what, String path, long deadline) {
        CuratorEvent read = zk.call(what, deadline, callback -> zk.client().getData().inBackground(callback).forPath(path));
        Node node = Node.of(what, path, read);
        return node.stat == null ? Optional.empty() : Optional.of(node);
    }

    /**
     * Writes the new state of a node at the version read.
     *
     * @return the stat of the write, or empty if the node changed since the read
     */
    private Optional<Stat> write(String what, Node node, SlotState state, long deadline) {
        byte[] data = state.bytes();
        int version = node.stat.getVersion();
        CuratorEvent write = zk.call(what, deadline,
            callback -> zk.client().setData().withVersion(version).inBackground(callback).forPath(node.path, data));
        return switch (Zk.code(write)) {
            case OK -> Optional.of(Zk.stat(what, write));
            case BADVERSION, NONODE -> Optional.empty();
            default -> throw Zk.unexpected(what, write);
        };
    }

    private CompletableFuture<CuratorEvent> touchClock(String nsPath) {
        String path = nsPath + "/" + CLOCK;
        return zk.start(callback -> zk.client().setData().inBackground(callback).forPath(path, new byte[0]));
    }

    /** The {@code mtime} of the clock write: the store's {@code now} (06 §2.4). */
    private long clockNow(String nsPath, CompletableFuture<CuratorEvent> clock, long deadline) {
        String path = nsPath + "/" + CLOCK;
        String what = "clock " + path;
        CuratorEvent event = zk.await(what, deadline, clock);
        while (Zk.code(event) == KeeperException.Code.NONODE) {
            CuratorEvent create = zk.call(what, deadline,
                callback -> zk.client().create().withMode(CreateMode.PERSISTENT).inBackground(callback).forPath(path, new byte[0]));
            switch (Zk.code(create)) {
                case OK, NODEEXISTS -> event = zk.await(what, deadline, touchClock(nsPath));
                default -> throw Zk.unexpected(what, create);
            }
        }
        if (Zk.code(event) != KeeperException.Code.OK) {
            throw Zk.unexpected(what, event);
        }
        return Zk.stat(what, event).getMtime();
    }

    /**
     * Records the namespace metadata on first use, or checks it against the recorded one (06 §1).
     */
    private void checkMetadata(String namespace, String nsPath, Metadata expected, long deadline) {
        Metadata known = namespaces.get(namespace);
        if (known == null) {
            known = readOrRecord(nsPath, expected, deadline);
            namespaces.putIfAbsent(namespace, known);
        }
        if (!known.equals(expected)) {
            throw new NamespaceMismatchException(namespace, "first used with limit " + known.limit + " and tolerance " + known.toleranceMillis
                + "ms, now " + expected.limit + " and " + expected.toleranceMillis + "ms");
        }
    }

    private Metadata readOrRecord(String nsPath, Metadata expected, long deadline) {
        String what = "metadata " + nsPath;
        byte[] data = expected.bytes();
        for (int attempt = 0; ; attempt++) {
            CuratorEvent read = zk.call(what, deadline, callback -> zk.client().getData().inBackground(callback).forPath(nsPath));
            switch (Zk.code(read)) {
                case OK -> {
                    Metadata recorded = Metadata.parse(nsPath, read.getData());
                    if (recorded != null) {
                        return recorded;
                    }
                    int version = read.getStat().getVersion();
                    CuratorEvent write = zk.call(what, deadline,
                        callback -> zk.client().setData().withVersion(version).inBackground(callback).forPath(nsPath, data));
                    switch (Zk.code(write)) {
                        case OK -> {
                            return expected;
                        }
                        case BADVERSION, NONODE -> Zk.backoff(attempt, deadline, what);
                        default -> throw Zk.unexpected(what, write);
                    }
                }
                case NONODE -> {
                    CuratorEvent create = zk.call(what, deadline, callback -> zk.client().create().creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT).inBackground(callback).forPath(nsPath, data));
                    switch (Zk.code(create)) {
                        case OK -> {
                            return expected;
                        }
                        case NODEEXISTS -> Zk.backoff(attempt, deadline, what);
                        default -> throw Zk.unexpected(what, create);
                    }
                }
                default -> throw Zk.unexpected(what, read);
            }
        }
    }

    private static long positiveMillis(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive: " + duration);
        }
        return duration.toMillis();
    }

    private static long toleranceMillis(Duration tolerance) {
        Objects.requireNonNull(tolerance, "tolerance");
        if (tolerance.isNegative()) {
            throw new IllegalArgumentException("tolerance must not be negative: " + tolerance);
        }
        return tolerance.toMillis();
    }

    /**
     * A slot node as read.
     *
     * @param path  the node path
     * @param state the slot state, {@link SlotState#NEW} if the node does not exist
     * @param stat  the stat, null if the node does not exist
     * @param mtime the modification time, 0 if the node does not exist
     */
    private record Node(String path, SlotState state, Stat stat, long mtime) {
        static Node of(String what, String path, CuratorEvent read) {
            return switch (Zk.code(read)) {
                case OK -> new Node(path, SlotState.parse(path, read.getData()), read.getStat(), read.getStat().getMtime());
                case NONODE -> new Node(path, SlotState.NEW, null, 0);
                default -> throw Zk.unexpected(what, read);
            };
        }

        boolean free(long now) {
            return state.free(mtime, now);
        }
    }

    /** The namespace metadata (06 §1). */
    private record Metadata(int limit, long toleranceMillis) {
        byte[] bytes() {
            return ("limit=" + limit + "\ntolerance=" + toleranceMillis + "\n").getBytes(StandardCharsets.US_ASCII);
        }

        static Metadata parse(String path, byte[] data) {
            if (data == null || data.length == 0) {
                return null;
            }
            Integer limit = null;
            Long tolerance = null;
            try {
                for (String line : new String(data, StandardCharsets.US_ASCII).split("\n")) {
                    if (line.startsWith("limit=")) {
                        limit = Integer.parseInt(line.substring("limit=".length()));
                    } else if (line.startsWith("tolerance=")) {
                        tolerance = Long.parseLong(line.substring("tolerance=".length()));
                    }
                }
            } catch (NumberFormatException e) {
                throw new CosIdException("Namespace node " + path + " is unreadable", e);
            }
            if (limit == null || tolerance == null) {
                throw new CosIdException("Namespace node " + path + " is unreadable: " + new String(data, StandardCharsets.US_ASCII));
            }
            return new Metadata(limit, tolerance);
        }
    }
}
