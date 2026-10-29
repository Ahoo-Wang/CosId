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
import me.ahoo.cosid.RangeExhaustedException;
import me.ahoo.cosid.RangeSealedException;
import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;

import org.apache.curator.framework.api.CuratorEvent;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.common.PathUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link RangeStore} on ZooKeeper (06-stores.md §2.4): the node {@code /cosid/range/{key}} holds the next value as
 * decimal text, followed by {@value #SEALED} once the key is sealed. A reservation is a compare-and-set, a
 * {@code setData} at the version it read; a missing key is created at {@code initialOffset}, and a lost race to create
 * it is tried again. The keys of periodic segments, {@code {key}/{period}}, are children of the key node: a node that
 * so far is only a parent, with no data, is a key not used yet. Nodes are never deleted (S6).
 */
final class ZookeeperRangeStore implements RangeStore {
    static final String ROOT = "/cosid/range";
    static final String SEALED = ":sealed";
    private static final String RESERVE = "reserve";
    private static final byte[] SEALED_BYTES = Ascii.bytes(SEALED);
    private static final byte[] NO_BYTES = new byte[0];
    /** At most this many validated paths are kept; a store has a few keys (and one per period of a periodic one). */
    static final int MAX_CACHED_PATHS = 4096;

    private final Zk zk;
    /** The validated path of each key (R1-zk-01): validation copies the path into a char array. */
    private final Map<String, String> paths = new ConcurrentHashMap<>();
    /** What this store last read or wrote of each key, by path: a reserve first tries a compare-and-set on it. */
    private final Map<String, Seen> seen = new ConcurrentHashMap<>();
    /** After a compare-and-set on a stale {@link Seen}, this many reserves of the key read it first. */
    static final int CONTENDED_RESERVES = 8;

    ZookeeperRangeStore(Zk zk) {
        this.zk = Objects.requireNonNull(zk, "zk");
    }

    static String path(String key) {
        Objects.requireNonNull(key, "key");
        String path = ROOT + "/" + key;
        try {
            PathUtils.validatePath(path);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Range key [" + key + "] is no valid ZooKeeper path: " + e.getMessage(), e);
        }
        return path;
    }

    /** {@link #path(String)}, validated once per key. */
    String cachedPath(String key) {
        String path = paths.get(Objects.requireNonNull(key, "key"));
        if (path == null) {
            path = path(key);
            if (paths.size() < MAX_CACHED_PATHS) {
                paths.put(key, path);
            }
        }
        return path;
    }

    @Override
    public Range reserve(String key, long size, long initialOffset) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive: " + size);
        }
        if (initialOffset < 0) {
            throw new IllegalArgumentException("initialOffset must not be negative: " + initialOffset);
        }
        String path = cachedPath(key);
        long deadline = zk.deadline();
        // One round trip while no other client wrote the key since this store did: setData at the version it saw, which
        // matches only while the node still holds the value seen (a seal or another reserve changes the version).
        Seen last = seen.get(path);
        if (last != null && last.contended == 0) {
            long start = last.next;
            byte[] data = new Value(next(key, start, size), false).bytes();
            int version = last.version;
            CuratorEvent write = zk.call(RESERVE, path, deadline,
                callback -> zk.client().setData().withVersion(version).inBackground(callback).forPath(path, data));
            switch (Zk.code(write)) {
                case OK -> {
                    see(path, Zk.stat(RESERVE, path, write).getVersion(), start + size, 0);
                    return new Range(start, start + size);
                }
                case BADVERSION, NONODE -> see(path, -1, 0, CONTENDED_RESERVES);
                default -> throw Zk.unexpected(RESERVE, path, write);
            }
        }
        for (int attempt = 0; ; attempt++) {
            CuratorEvent read = zk.call(RESERVE, path, deadline, callback -> zk.client().getData().inBackground(callback).forPath(path));
            switch (Zk.code(read)) {
                case OK -> {
                    Value value = Value.parse(path, read.getData());
                    if (value != null && value.sealed()) {
                        throw new RangeSealedException(key);
                    }
                    long start = value == null ? initialOffset : value.next();
                    byte[] data = new Value(next(key, start, size), false).bytes();
                    int version = read.getStat().getVersion();
                    CuratorEvent write = zk.call(RESERVE, path, deadline,
                        callback -> zk.client().setData().withVersion(version).inBackground(callback).forPath(path, data));
                    switch (Zk.code(write)) {
                        case OK -> {
                            Seen before = seen.get(path);
                            see(path, Zk.stat(RESERVE, path, write).getVersion(), start + size,
                                before == null ? 0 : Math.max(0, before.contended - 1));
                            return new Range(start, start + size);
                        }
                        case BADVERSION, NONODE -> Zk.backoff(attempt, deadline, RESERVE, path);
                        default -> throw Zk.unexpected(RESERVE, path, write);
                    }
                }
                case NONODE -> {
                    byte[] data = new Value(next(key, initialOffset, size), false).bytes();
                    CuratorEvent create = zk.call(RESERVE, path, deadline, callback -> zk.client().create().creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT).inBackground(callback).forPath(path, data));
                    switch (Zk.code(create)) {
                        case OK -> {
                            return new Range(initialOffset, initialOffset + size);
                        }
                        case NODEEXISTS -> Zk.backoff(attempt, deadline, RESERVE, path);
                        default -> throw Zk.unexpected(RESERVE, path, create);
                    }
                }
                default -> throw Zk.unexpected(RESERVE, path, read);
            }
        }
    }

    /**
     * Records the version and value of a key after a write, or only how many reserves read it first ({@code version}
     * -1). At most {@link #MAX_CACHED_PATHS} keys are kept.
     */
    private void see(String path, int version, long next, int contended) {
        if (version < 0 && contended == 0) {
            seen.remove(path);
        } else if (seen.size() < MAX_CACHED_PATHS || seen.containsKey(path)) {
            seen.put(path, version < 0 ? new Seen(-1, 0, contended) : new Seen(version, next, contended));
        }
    }

    /**
     * The node of a key as this store last wrote it.
     *
     * @param version   its version, -1 when unknown
     * @param next      the value then
     * @param contended reserves that read the node first, after a compare-and-set on a stale value
     */
    private record Seen(int version, long next, int contended) {
    }

    private static long next(String key, long start, long size) {
        if (start > Long.MAX_VALUE - size) {
            throw new RangeExhaustedException(key);
        }
        return start + size;
    }

    /**
     * Seals the key; a key not created yet is created sealed, so it never issues a range (S6).
     *
     * @param key the range key
     * @return true
     */
    @Override
    public boolean seal(String key) {
        String path = cachedPath(key);
        seen.remove(path);
        String what = "seal " + path;
        long deadline = zk.deadline();
        for (int attempt = 0; ; attempt++) {
            CuratorEvent read = zk.call(what, deadline, callback -> zk.client().getData().inBackground(callback).forPath(path));
            switch (Zk.code(read)) {
                case OK -> {
                    Value value = Value.parse(path, read.getData());
                    if (value != null && value.sealed()) {
                        return true;
                    }
                    byte[] data = new Value(value == null ? 0 : value.next(), true).bytes();
                    int version = read.getStat().getVersion();
                    CuratorEvent write = zk.call(what, deadline,
                        callback -> zk.client().setData().withVersion(version).inBackground(callback).forPath(path, data));
                    switch (Zk.code(write)) {
                        case OK -> {
                            return true;
                        }
                        case BADVERSION, NONODE -> Zk.backoff(attempt, deadline, what);
                        default -> throw Zk.unexpected(what, write);
                    }
                }
                case NONODE -> {
                    byte[] data = new Value(0, true).bytes();
                    CuratorEvent create = zk.call(what, deadline, callback -> zk.client().create().creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT).inBackground(callback).forPath(path, data));
                    switch (Zk.code(create)) {
                        case OK -> {
                            return true;
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
     * The data of a range node.
     *
     * @param next   the start of the next range
     * @param sealed whether the key is sealed
     */
    record Value(long next, boolean sealed) {
        byte[] bytes() {
            return Ascii.number(next, sealed ? SEALED_BYTES : NO_BYTES);
        }

        /**
         * Parses node data.
         *
         * @return the value, or null for a node without data: a parent of other keys, not used as a key yet
         */
        static Value parse(String path, byte[] data) {
            if (data == null || data.length == 0) {
                return null;
            }
            boolean sealed = data.length > SEALED_BYTES.length
                && Ascii.is(data, data.length - SEALED_BYTES.length, data.length, SEALED_BYTES);
            try {
                return new Value(Ascii.number(data, 0, sealed ? data.length - SEALED_BYTES.length : data.length), sealed);
            } catch (NumberFormatException | ArithmeticException e) {
                throw new CosIdException("Range node " + path + " holds no range value: " + new String(data, StandardCharsets.US_ASCII), e);
            }
        }
    }
}
