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

package me.ahoo.cosid.zookeeper;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.CosIdException;
import me.ahoo.cosid.internal.Checks;
import me.ahoo.cosid.segment.AbstractIdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.util.CallTimeout;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Zookeeper IdSegment Distributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class ZookeeperIdSegmentDistributor extends AbstractIdSegmentDistributor {
    
    /**
     * Compare-and-set attempts of one fetch before it takes the {@code -locker} lock: a conflict means another instance
     * fetched in between.
     */
    static final int OPTIMISTIC_ATTEMPTS = 8;
    /**
     * How long a fetch tries when it has no call timeout: the lock timeout of the 3.x and 4.0 distributors.
     */
    static final long DEFAULT_DEADLINE_MILLIS = 15_000;
    private static final long MAX_BACKOFF_MILLIS = 32;
    private final long offset;
    /**
     * /cosid/{namespace}.{name}
     */
    private final String counterPath;
    /**
     * {counterPath}-locker: the lock of the 3.x and 4.0 distributors, taken after {@link #OPTIMISTIC_ATTEMPTS}
     * conflicts, as they do.
     */
    private final String counterLockerPath;
    private final CuratorFramework curatorFramework;
    private final CallTimeout callTimeout;
    
    public ZookeeperIdSegmentDistributor(String namespace, String name, long offset, long step, CuratorFramework curatorFramework, RetryPolicy retryPolicy) {
        this(namespace, name, offset, step, curatorFramework, retryPolicy, CallTimeout.NONE);
    }
    
    /**
     * Create a ZooKeeper segment distributor whose {@link #nextMaxId(long)} gives up after {@code callTimeout}.
     * {@link #ensureOffset()} at startup is not bounded.
     *
     * <p>The counter is the 8-byte big-endian long that 3.x and 4.0 ({@code DistributedAtomicLong}) read and write, changed
     * with a versioned {@code setData}: a compare-and-set that works next to them. A conflict, another instance having
     * fetched in between, is tried again after a short random pause; after {@link #OPTIMISTIC_ATTEMPTS} of them the
     * fetch takes their {@code -locker} lock, shared with 3.x and 4.0, and keeps trying under it. A fetch gives up at
     * {@code callTimeout}, or after {@link #DEFAULT_DEADLINE_MILLIS} without one.</p>
     *
     * @param retryPolicy not used for conflicts since 4.1 (Curator's client retries connection losses)
     */
    public ZookeeperIdSegmentDistributor(String namespace, String name, long offset, long step, CuratorFramework curatorFramework, RetryPolicy retryPolicy,
                                         CallTimeout callTimeout) {
        super(namespace, name, step);
        Checks.checkArgument(offset >= 0, "offset:[%s] must be greater than or equal to 0!", offset);
        this.offset = offset;
        this.counterPath = Checks.lenientFormat("/%s/%s", CosId.COSID, getNamespacedName());
        this.counterLockerPath = counterPath + "-locker";
        this.curatorFramework = curatorFramework;
        this.callTimeout = callTimeout;
    }
    
    String getCounterLockerPath() {
        return counterLockerPath;
    }
    
    static byte[] toBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }
    
    /**
     * The counter as {@code DistributedAtomicLong} reads it: empty is 0.
     */
    static long fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return 0;
        }
        return ByteBuffer.wrap(bytes).getLong();
    }
    
    void ensureOffset() {
        if (log.isDebugEnabled()) {
            log.debug("Ensure Offset [{}] offset:[{}].", counterPath, offset);
        }
        boolean notExists;
        try {
            notExists = createIfMissing();
        } catch (Exception exception) {
            throw new CosIdException(exception.getMessage(), exception);
        }
        
        if (log.isDebugEnabled()) {
            log.debug("Ensure Offset [{}] offset:[{}] - notExists:[{}].", counterPath, offset, notExists);
        }
    }
    
    private boolean createIfMissing() throws Exception {
        try {
            curatorFramework.create().creatingParentsIfNeeded().forPath(counterPath, toBytes(offset));
            return true;
        } catch (KeeperException.NodeExistsException nodeExistsException) {
            return false;
        }
    }
    
    @Override
    protected long doNextMaxId(long step) {
        if (log.isDebugEnabled()) {
            log.debug("Next MaxId [{}] step:[{}].", counterPath, step);
        }
        
        long nextMaxId = callTimeout.call("nextMaxId", () -> compareAndAdd(step));
        
        if (log.isDebugEnabled()) {
            log.debug("Next MaxId [{}] step:[{}] - nextMaxId:[{}].", counterPath, step, nextMaxId);
        }
        return nextMaxId;
    }
    
    private long compareAndAdd(long step) throws Exception {
        long deadline = System.currentTimeMillis() + (callTimeout.isEnabled() ? callTimeout.getTimeout().toMillis() : DEFAULT_DEADLINE_MILLIS);
        for (int attempt = 0; attempt < OPTIMISTIC_ATTEMPTS; attempt++) {
            Long next = tryCompareAndAdd(step);
            if (next != null) {
                return next;
            }
            backoff(attempt);
        }
        // Contended: queue on the lock of 3.x and 4.0, so that the instances under it take turns instead of colliding.
        InterProcessMutex lock = new InterProcessMutex(curatorFramework, counterLockerPath);
        if (!lock.acquire(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS)) {
            throw conflict();
        }
        try {
            for (int attempt = 0; ; attempt++) {
                Long next = tryCompareAndAdd(step);
                if (next != null) {
                    return next;
                }
                // An instance outside the lock still in its optimistic attempts.
                if (System.currentTimeMillis() >= deadline) {
                    throw conflict();
                }
                backoff(attempt);
            }
        } finally {
            lock.release();
        }
    }
    
    /**
     * One versioned read-add-write.
     *
     * @return the new max id, or {@code null} on a conflict
     */
    private @Nullable Long tryCompareAndAdd(long step) throws Exception {
        Stat stat = new Stat();
        byte[] current;
        try {
            current = curatorFramework.getData().storingStatIn(stat).forPath(counterPath);
        } catch (KeeperException.NoNodeException noNodeException) {
            createIfMissing();
            return null;
        }
        long previous = fromBytes(current);
        long next;
        try {
            next = Math.addExact(previous, step);
        } catch (ArithmeticException overflow) {
            throw new CosIdException(Checks.lenientFormat("nextMaxId - [%s][%s + %s] overflows!", counterPath, previous, step), overflow);
        }
        try {
            curatorFramework.setData().withVersion(stat.getVersion()).forPath(counterPath, toBytes(next));
            return next;
        } catch (KeeperException.BadVersionException conflict) {
            return null;
        }
    }
    
    /**
     * A random pause, growing with the attempts, so that colliding instances spread out.
     */
    private static void backoff(int attempt) throws InterruptedException {
        long bound = Math.min(MAX_BACKOFF_MILLIS, 1L << Math.min(attempt, 5));
        Thread.sleep(ThreadLocalRandom.current().nextLong(bound + 1));
    }
    
    private CosIdException conflict() {
        return new CosIdException(Checks.lenientFormat("nextMaxId - [%s] concurrency conflict!", counterPath));
    }
}
