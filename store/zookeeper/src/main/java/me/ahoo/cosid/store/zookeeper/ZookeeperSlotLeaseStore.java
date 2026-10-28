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
import org.apache.curator.framework.api.transaction.CuratorOp;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Op;
import org.apache.zookeeper.OpResult;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.common.PathUtils;
import org.apache.zookeeper.data.Stat;

import java.net.URLEncoder;
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
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

/**
 * {@link SlotLeaseStore} on ZooKeeper (06-stores.md §2.4).
 *
 * <ul>
 *     <li>{@code /cosid/slot/{ns}}: the namespace metadata {@code limit} and {@code tolerance}, recorded on first use;</li>
 *     <li>{@code /cosid/slot/{ns}/{slot}}: a slot, {@link SlotState}; created by its first acquire, never deleted;</li>
 *     <li>{@code /cosid/slot/{ns}/.clock}: written by every acquire, its {@code mtime} is the {@code now} of the
 *     expiry check;</li>
 *     <li>{@code /cosid/owner/{ns}/{ownerKey}}: the owner index, the slots the key held, written in the same
 *     {@code multi} as each acquire.</li>
 * </ul>
 *
 * <p>Every state change (05 §2.2) is a compare-and-set of one slot node at the version read (an acquire also writes
 * the owner index in the same {@code multi}). Times are the {@code mtime} of the write, stamped by the ZooKeeper
 * leader; {@code storeNow} and {@code expiresAt} come from the same write. Uniqueness depends on none of them, nor on
 * the index (05 §3): each holder's floor is the ceiling the previous write left in the node it replaces. Acquire reads
 * the slots of the owner index and the preferred slot, then the lowest slots in growing ascending batches until a free
 * one (read-only {@code multi}), trying them in the order of 05 §2.1; a release writes the slot node only.
 */
final class ZookeeperSlotLeaseStore implements SlotLeaseStore {
    static final String ROOT = "/cosid/slot";
    static final String CLOCK = ".clock";
    static final String OWNER_ROOT = "/cosid/owner";
    static final int FIRST_BATCH = 32;
    static final int MAX_BATCH = 256;

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

    /** The owner index of a key: the key as a node name, URL-encoded with {@code .} and {@code *} escaped too. */
    static String ownerPath(String namespace, String ownerKey) {
        namespacePath(namespace);
        String name = URLEncoder.encode(ownerKey, StandardCharsets.UTF_8).replace(".", "%2E").replace("*", "%2A");
        return OWNER_ROOT + "/" + namespace + "/" + name;
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
        String ownerPath = ownerPath(namespace, owner.key());
        String what = "acquire " + nsPath;
        long deadline = zk.deadline();
        checkMetadata(namespace, nsPath, new Metadata(limit, toleranceMillis), deadline);
        Request request = new Request(namespace, nsPath, ownerPath, owner, limit, ttlMillis, toleranceMillis, deadline);
        for (int attempt = 0; ; attempt++) {
            // The clock write and the index read go out together; ZooKeeper answers them in order, one round trip.
            CompletableFuture<CuratorEvent> clock = touchClock(nsPath);
            CompletableFuture<CuratorEvent> indexRead = zk.start(callback -> zk.client().getData().inBackground(callback).forPath(ownerPath));
            long now = clockNow(nsPath, clock, deadline);
            OwnerIndex index = OwnerIndex.of(what, ownerPath, zk.await(what, deadline, indexRead), limit);
            Slots slots = new Slots(what, nsPath, limit, deadline);
            int preferred = preferredSlot.isPresent() && preferredSlot.getAsInt() >= 0 && preferredSlot.getAsInt() < limit
                ? preferredSlot.getAsInt() : -1;
            Optional<AcquireResult> result = slots.read(index.withPreferred(preferred))
                ? choose(request, index, slots, now, preferred) : Optional.empty();
            if (result.isPresent()) {
                return result.get();
            }
            Zk.backoff(attempt, deadline, what);
        }
    }

    /**
     * One pass of the slot choice (05 §2.1). The owner index gives the slots the key held: every slot the key holds is
     * in it, since the index is written in the same {@code multi} as each acquire, so the idempotent, duplicate and
     * sticky steps read those slots only. The lowest free slot is then searched in ascending batches that grow from
     * {@value #FIRST_BATCH} to {@value #MAX_BATCH}, stopping at the first free one; only an exhausted namespace reads
     * every slot.
     *
     * @return the result, or empty if a compare-and-set lost a race (or the connection) and the acquire starts over
     */
    private Optional<AcquireResult> choose(Request request, OwnerIndex index, Slots slots, long now, int preferred) {
        Owner owner = request.owner;
        boolean duplicate = false;
        for (int slot : index.slots) {
            SlotState state = slots.node(slot).state;
            duplicate |= !slots.node(slot).free(now) && state.ownerKey().equals(owner.key()) && !state.incarnation().equals(owner.incarnation());
        }
        for (int slot : index.slots) {
            Node node = slots.node(slot);
            if (owner.equals(node.state.owner())) {
                return Optional.of(AcquireResult.Acquired.builder(node.state.toLease(request.namespace, slot, node.mtime, now), AcquireReason.IDEMPOTENT)
                    .duplicateOwnerKey(duplicate)
                    .build());
            }
        }
        if (preferred >= 0 && slots.node(preferred).free(now)) {
            return take(request, preferred, slots.node(preferred), index, slots, AcquireReason.PREFERRED, duplicate);
        }
        for (int slot : index.slots) {
            Node node = slots.node(slot);
            if (owner.key().equals(node.state.lastOwnerKey()) && node.free(now)) {
                return take(request, slot, node, index, slots, AcquireReason.STICKY, duplicate);
            }
        }
        long earliestExpiresAt = Long.MAX_VALUE;
        int batch = FIRST_BATCH;
        for (int from = 0; from < request.limit; from += batch, batch = Math.min(batch * 2, MAX_BATCH)) {
            int to = (int) Math.min(request.limit, (long) from + batch);
            if (!slots.readRange(from, to)) {
                return Optional.empty();
            }
            for (int slot = from; slot < to; slot++) {
                Node node = slots.node(slot);
                if (node.free(now)) {
                    return take(request, slot, node, index, slots, AcquireReason.LOWEST, duplicate);
                }
                earliestExpiresAt = Math.min(earliestExpiresAt, node.state.expiresAt(node.mtime));
            }
        }
        return Optional.of(new AcquireResult.Exhausted(earliestExpiresAt));
    }

    /**
     * Takes a free slot: one {@code multi} writes the slot node at the version read and the owner index at the version
     * read (or creates it), so the index never misses a slot the key holds. A slot never used gets an empty node
     * first, so the {@code multi} can answer the stat of the slot write. The index keeps the slots whose last owner
     * is still the key.
     */
    private Optional<AcquireResult> take(Request request, int slot, Node node, OwnerIndex index, Slots slots, AcquireReason reason,
                                         boolean duplicate) {
        String path = node.path;
        String what = "acquire " + path;
        long deadline = request.deadline;
        int version;
        if (node.stat == null) {
            CuratorEvent create = zk.call(what, deadline,
                callback -> zk.client().create().withMode(CreateMode.PERSISTENT).inBackground(callback).forPath(path, new byte[0]));
            switch (Zk.code(create)) {
                case OK -> version = 0;
                case NODEEXISTS -> {
                    return Optional.empty();
                }
                default -> throw Zk.unexpected(what, create);
            }
        } else {
            version = node.stat.getVersion();
        }
        SlotState taken = node.state.take(request.owner, node.mtime, request.ttlMillis, request.toleranceMillis);
        byte[] slotData = taken.bytes();
        byte[] indexData = index.with(slot, kept -> request.owner.key().equals(slots.node(kept).state.lastOwnerKey()));
        CuratorOp slotWrite;
        CuratorOp indexWrite;
        try {
            slotWrite = zk.client().transactionOp().setData().withVersion(version).forPath(path, slotData);
            indexWrite = index.stat == null
                ? zk.client().transactionOp().create().withMode(CreateMode.PERSISTENT).forPath(request.ownerPath, indexData)
                : zk.client().transactionOp().setData().withVersion(index.stat.getVersion()).forPath(request.ownerPath, indexData);
        } catch (Exception e) {
            throw new CosIdException("ZooKeeper " + what + " failed: " + e, e);
        }
        CuratorEvent multi = zk.call(what, deadline, callback -> zk.client().transaction().inBackground(callback).forOperations(slotWrite, indexWrite));
        switch (Zk.code(multi)) {
            case OK -> {
                Stat stat = multi.getOpResults().get(0).getResultStat();
                if (stat == null) {
                    throw new CosIdException("ZooKeeper " + what + " returned no stat");
                }
                long mtime = stat.getMtime();
                return Optional.of(AcquireResult.Acquired.builder(taken.toLease(request.namespace, slot, mtime, mtime), reason)
                    .duplicateOwnerKey(duplicate)
                    .build());
            }
            case NONODE -> {
                // The parent of the owner index is created on first need.
                ensureOwnerParent(what, request.ownerPath, deadline);
                return Optional.empty();
            }
            case BADVERSION, NODEEXISTS -> {
                return Optional.empty();
            }
            default -> throw Zk.unexpected(what, multi);
        }
    }

    private void ensureOwnerParent(String what, String ownerPath, long deadline) {
        String parent = ZKPaths.getPathAndNode(ownerPath).getPath();
        CuratorEvent create = zk.call(what, deadline, callback -> zk.client().create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT)
            .inBackground(callback).forPath(parent, new byte[0]));
        switch (Zk.code(create)) {
            case OK, NODEEXISTS -> {
            }
            default -> throw Zk.unexpected(what, create);
        }
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

    /**
     * The slot nodes of one acquire attempt, read on demand: read-only {@code multi} requests of at most
     * {@value #MAX_BATCH} {@code getData} each, on the ZooKeeper handle of the client (a slot never used reads as
     * {@code NONODE}). One request per batch keeps the allocation and the round trips low, where a Curator background
     * call per slot does not.
     */
    private final class Slots {
        private final String what;
        private final String nsPath;
        private final long deadline;
        private final Node[] nodes;

        private Slots(String what, String nsPath, int limit, long deadline) {
            this.what = what;
            this.nsPath = nsPath;
            this.deadline = deadline;
            this.nodes = new Node[limit];
        }

        Node node(int slot) {
            return nodes[slot];
        }

        boolean readRange(int from, int to) {
            int[] range = new int[to - from];
            for (int slot = from; slot < to; slot++) {
                range[slot - from] = slot;
            }
            return read(range);
        }

        /**
         * Reads the slots not read yet.
         *
         * @return false if the connection was lost and the acquire must start over
         */
        boolean read(int[] slots) {
            int[] missing = Arrays.stream(slots).filter(slot -> nodes[slot] == null).distinct().toArray();
            if (missing.length == 0) {
                return true;
            }
            ZooKeeper zooKeeper;
            try {
                zooKeeper = zk.client().getZookeeperClient().getZooKeeper();
            } catch (Exception e) {
                throw new CosIdException("ZooKeeper " + what + ": no connection", e);
            }
            String clientNamespace = zk.client().getNamespace();
            List<CompletableFuture<List<OpResult>>> reads = new ArrayList<>(missing.length / MAX_BATCH + 1);
            for (int from = 0; from < missing.length; from += MAX_BATCH) {
                List<Op> ops = new ArrayList<>(Math.min(MAX_BATCH, missing.length - from));
                for (int i = from; i < Math.min(missing.length, from + MAX_BATCH); i++) {
                    ops.add(Op.getData(ZKPaths.fixForNamespace(clientNamespace, nsPath + "/" + missing[i])));
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
            int index = 0;
            for (CompletableFuture<List<OpResult>> read : reads) {
                List<OpResult> results;
                try {
                    results = zk.await(what, deadline, read);
                } catch (CosIdException e) {
                    if (e.getCause() instanceof KeeperException.ConnectionLossException) {
                        return false;
                    }
                    throw e;
                }
                for (OpResult result : results) {
                    int slot = missing[index++];
                    String path = nsPath + "/" + slot;
                    if (result instanceof OpResult.GetDataResult data) {
                        nodes[slot] = new Node(path, SlotState.parse(path, data.getData()), data.getStat(), data.getStat().getMtime());
                    } else if (result instanceof OpResult.ErrorResult error && error.getErr() == KeeperException.Code.NONODE.intValue()) {
                        nodes[slot] = new Node(path, SlotState.NEW, null, 0);
                    } else if (result instanceof OpResult.ErrorResult error) {
                        throw new CosIdException("ZooKeeper " + what + " failed reading " + path + ": " + KeeperException.Code.get(error.getErr()));
                    } else {
                        throw new CosIdException("ZooKeeper " + what + " returned " + result + " for " + path);
                    }
                }
            }
            return true;
        }
    }

    /** The arguments of one acquire. */
    private record Request(String namespace, String nsPath, String ownerPath, Owner owner, int limit, long ttlMillis, long toleranceMillis,
                           long deadline) {
    }

    /**
     * The owner index {@code /cosid/owner/{ns}/{ownerKey}}: the slots, ascending, whose last owner was the key when
     * the index was written; a superset of the slots the key holds.
     *
     * @param stat  the stat, null if the key never acquired in the namespace
     * @param slots the slots below the limit
     */
    record OwnerIndex(Stat stat, int[] slots) {
        static OwnerIndex of(String what, String path, CuratorEvent read, int limit) {
            return switch (Zk.code(read)) {
                case OK -> new OwnerIndex(read.getStat(), parse(path, read.getData(), limit));
                case NONODE -> new OwnerIndex(null, new int[0]);
                default -> throw Zk.unexpected(what, read);
            };
        }

        static int[] parse(String path, byte[] data, int limit) {
            if (data == null || data.length == 0) {
                return new int[0];
            }
            String text = new String(data, StandardCharsets.US_ASCII).trim();
            try {
                return Arrays.stream(text.split(",")).filter(part -> !part.isEmpty()).mapToInt(part -> Integer.parseInt(part.trim()))
                    .filter(slot -> slot >= 0 && slot < limit).sorted().distinct().toArray();
            } catch (NumberFormatException e) {
                throw new CosIdException("Owner index " + path + " is unreadable: " + text, e);
            }
        }

        int[] withPreferred(int preferred) {
            if (preferred < 0) {
                return slots;
            }
            int[] all = Arrays.copyOf(slots, slots.length + 1);
            all[slots.length] = preferred;
            return all;
        }

        /** The index after the key takes {@code slot}: that slot and the listed ones whose last owner is still the key. */
        byte[] with(int slot, IntPredicate keep) {
            int[] next = IntStream.concat(Arrays.stream(slots).filter(keep), IntStream.of(slot))
                .sorted().distinct().toArray();
            StringBuilder text = new StringBuilder(next.length * 5);
            for (int i = 0; i < next.length; i++) {
                text.append(i == 0 ? "" : ",").append(next[i]);
            }
            return text.toString().getBytes(StandardCharsets.US_ASCII);
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
