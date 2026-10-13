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
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

import java.nio.ByteBuffer;

/**
 * Zookeeper IdSegment Distributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class ZookeeperIdSegmentDistributor extends AbstractIdSegmentDistributor {
    
    /**
     * Compare-and-set attempts of one fetch before it gives up: a conflict means another instance fetched in between.
     */
    static final int MAX_ATTEMPTS = 100;
    private final long offset;
    /**
     * /cosid/{namespace}.{name}
     */
    private final String counterPath;
    /**
     * {counterPath}-locker: the lock of the 3.x and 4.0 distributors, never taken here.
     */
    private final String counterLockerPath;
    private final CuratorFramework curatorFramework;
    private final RetryPolicy retryPolicy;
    private final CallTimeout callTimeout;
    
    public ZookeeperIdSegmentDistributor(String namespace, String name, long offset, long step, CuratorFramework curatorFramework, RetryPolicy retryPolicy) {
        this(namespace, name, offset, step, curatorFramework, retryPolicy, CallTimeout.NONE);
    }
    
    /**
     * Create a ZooKeeper segment distributor whose {@link #nextMaxId(long)} gives up after {@code callTimeout}.
     * {@link #ensureOffset()} at startup is not bounded.
     *
     * <p>The counter is the 8-byte big-endian long that 3.x and 4.0 ({@code DistributedAtomicLong}) read and write, changed
     * with a versioned {@code setData}: a compare-and-set that works next to them, without their 15 second lock.</p>
     */
    public ZookeeperIdSegmentDistributor(String namespace, String name, long offset, long step, CuratorFramework curatorFramework, RetryPolicy retryPolicy,
                                         CallTimeout callTimeout) {
        super(namespace, name, step);
        Checks.checkArgument(offset >= 0, "offset:[%s] must be greater than or equal to 0!", offset);
        this.offset = offset;
        this.counterPath = Checks.lenientFormat("/%s/%s", CosId.COSID, getNamespacedName());
        this.counterLockerPath = counterPath + "-locker";
        this.curatorFramework = curatorFramework;
        this.retryPolicy = retryPolicy;
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
        long started = System.currentTimeMillis();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Stat stat = new Stat();
            byte[] current;
            try {
                current = curatorFramework.getData().storingStatIn(stat).forPath(counterPath);
            } catch (KeeperException.NoNodeException noNodeException) {
                createIfMissing();
                continue;
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
                // Another instance fetched in between: read again, after the retry policy's pause.
                if (!retryPolicy.allowRetry(attempt, System.currentTimeMillis() - started, (time, unit) -> unit.sleep(time))) {
                    break;
                }
            }
        }
        throw new CosIdException(Checks.lenientFormat("nextMaxId - [%s] concurrency conflict!", counterPath));
    }
}
