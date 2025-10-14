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

package me.ahoo.cosid.segment;

import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;

import me.ahoo.cosid.segment.grouped.Grouped;
import me.ahoo.cosid.segment.grouped.GroupedKey;
import me.ahoo.cosid.util.Clock;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import jakarta.annotation.Nonnull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;


/**
 * ID segment distributor for allocating contiguous blocks of IDs in distributed systems.
 * 
 * <p>This interface provides the contract for distributing segments (contiguous blocks)
 * of IDs across distributed instances. It is a key component of the segment-based
 * ID generation algorithm, ensuring that each instance receives unique ranges of IDs.
 * 
 * <p>The distributor is responsible for:
 * <ul>
 *   <li>Allocating unique ID segments within a namespace</li>
 *   <li>Managing segment size (step) configuration</li>
 *   <li>Providing segment chain functionality for advanced use cases</li>
 *   <li>Supporting time-to-live for segments</li>
 * </ul>
 * 
 * <p>Common implementations include:
 * <ul>
 *   <li>Redis-based distribution</li>
 *   <li>ZooKeeper-based distribution</li>
 *   <li>JDBC-based distribution</li>
 * </ul>
 *
 * @author ahoo wang
 */
public interface IdSegmentDistributor extends Grouped {
    /**
     * The default number of segments to allocate at once.
     */
    int DEFAULT_SEGMENTS = 1;
    
    /**
     * The default offset for ID segments.
     */
    long DEFAULT_OFFSET = 0;
    
    /**
     * The default step size for ID segments.
     * 
     * <p>This determines the size of each allocated segment, representing
     * the number of IDs in each contiguous block.
     */
    long DEFAULT_STEP = 10;
    
    /**
     * Get the namespace for this distributor.
     * 
     * <p>The namespace provides a logical grouping for ID segments,
     * allowing multiple independent segment spaces within the same system.
     *
     * @return The namespace
     */
    @Nonnull
    String getNamespace();
    
    /**
     * Get the name for this distributor.
     * 
     * <p>The name uniquely identifies this distributor within its namespace.
     *
     * @return The name
     */
    @Nonnull
    String getName();
    
    /**
     * Get the namespaced name for this distributor.
     * 
     * <p>This is a convenience method that combines the namespace and name
     * into a single string identifier.
     *
     * @return The namespaced name (namespace.name)
     */
    default String getNamespacedName() {
        return getNamespacedName(getNamespace(), getName());
    }
    
    /**
     * Create a namespaced name from namespace and name components.
     * 
     * @param namespace The namespace
     * @param name The name
     * @return The namespaced name (namespace.name)
     */
    static String getNamespacedName(String namespace, String name) {
        return namespace + "." + name;
    }
    
    /**
     * Get the step size for ID segments.
     * 
     * <p>The step size determines how many IDs are allocated in each segment.
     * Larger steps reduce coordination overhead but may lead to ID gaps.
     *
     * @return The step size
     */
    long getStep();
    
    /**
     * Get the total step size for the specified number of segments.
     * 
     * <p>This method calculates the total step size when allocating multiple
     * segments at once, which is useful for prefetching scenarios.
     *
     * @param segments The number of segments
     * @return The total step size
     */
    default long getStep(int segments) {
        return Math.multiplyExact(getStep(), segments);
    }
    
    /**
     * Check if this distributor allows segment resetting.
     * 
     * <p>Some distributors support resetting to earlier segments, while others
     * only move forward to prevent ID conflicts.
     *
     * @return {@code true} if resetting is allowed, {@code false} otherwise
     */
    default boolean allowReset() {
        return GroupedKey.NEVER.equals(group());
    }
    
    /**
     * Allocate the next maximum ID for the specified step size.
     * 
     * <p>This method allocates a new range of IDs by returning the maximum
     * ID in the allocated range. The range starts at the previous maximum
     * plus one and ends at the returned value.
     *
     * @param step The step size for allocation
     * @return The maximum ID in the allocated range
     */
    long nextMaxId(long step);
    
    /**
     * Allocate the next maximum ID using the default step size.
     * 
     * @return The maximum ID in the allocated range
     */
    default long nextMaxId() {
        return nextMaxId(getStep());
    }
    
    /**
     * Allocate the next ID segment with infinite time-to-live.
     * 
     * @return The allocated ID segment
     */
    @Nonnull
    default IdSegment nextIdSegment() {
        return nextIdSegment(TIME_TO_LIVE_FOREVER);
    }
    
    /**
     * Allocate the next ID segment with the specified time-to-live.
     * 
     * @param ttl The time-to-live for the segment
     * @return The allocated ID segment
     */
    @Nonnull
    default IdSegment nextIdSegment(long ttl) {
        Preconditions.checkArgument(ttl > 0, "ttl:[%s] must be greater than 0.", ttl);
        
        final long maxId = nextMaxId();
        return new DefaultIdSegment(maxId, getStep(), Clock.SYSTEM.secondTime(), ttl, group());
    }
    
    /**
     * Allocate the next ID segment with multiple segments and specified time-to-live.
     * 
     * <p>This method allocates a merged segment that represents multiple
     * individual segments, useful for prefetching scenarios.
     *
     * @param segments The number of segments to allocate
     * @param ttl The time-to-live for the segment
     * @return The allocated merged ID segment
     */
    @Nonnull
    default IdSegment nextIdSegment(int segments, long ttl) {
        Preconditions.checkArgument(segments > 0, "segments:[%s] must be greater than 0.", segments);
        Preconditions.checkArgument(ttl > 0, "ttl:[%s] must be greater than 0.", ttl);
        
        final long totalStep = getStep(segments);
        final long maxId = nextMaxId(totalStep);
        final IdSegment nextIdSegment = new DefaultIdSegment(maxId, totalStep, Clock.SYSTEM.secondTime(), ttl, group());
        return new MergedIdSegment(segments, nextIdSegment);
    }
    
    /**
     * Allocate the next ID segment chain with default configuration.
     * 
     * @param previousChain The previous segment chain
     * @return The allocated ID segment chain
     */
    @Nonnull
    default IdSegmentChain nextIdSegmentChain(IdSegmentChain previousChain) {
        return nextIdSegmentChain(previousChain, DEFAULT_SEGMENTS, TIME_TO_LIVE_FOREVER);
    }
    
    /**
     * Allocate the next ID segment chain with custom configuration.
     * 
     * <p>This method allocates a new segment chain that extends the previous
     * chain, providing the foundation for the segment chain ID generation algorithm.
     *
     * @param previousChain The previous segment chain
     * @param segments The number of segments to allocate
     * @param ttl The time-to-live for the segment
     * @return The allocated ID segment chain
     */
    @Nonnull
    default IdSegmentChain nextIdSegmentChain(IdSegmentChain previousChain, int segments, long ttl) {
        if (DEFAULT_SEGMENTS == segments) {
            IdSegment nextIdSegment = nextIdSegment(ttl);
            return new IdSegmentChain(previousChain, nextIdSegment, allowReset());
        }
        
        IdSegment nextIdSegment = nextIdSegment(segments, ttl);
        return new IdSegmentChain(previousChain, nextIdSegment, allowReset());
    }
    
    /**
     * Ensure that the specified step size is valid.
     * 
     * @param step The step size to validate
     */
    static void ensureStep(long step) {
        Preconditions.checkArgument(step > 0, "step:[%s] must be greater than 0!", step);
    }
    
    
    /**
     * Atomic implementation of IdSegmentDistributor for testing purposes.
     * 
     * <p>This implementation uses atomic operations to allocate ID segments,
     * making it suitable for single-instance scenarios or testing.
     */
    class Atomic implements IdSegmentDistributor {
        private static final AtomicInteger ATOMIC_COUNTER = new AtomicInteger();
        private final long step;
        private final String name;
        private final AtomicLong adder = new AtomicLong();
        
        /**
         * Create a new Atomic distributor with default step size.
         */
        public Atomic() {
            this(DEFAULT_STEP);
        }
        
        /**
         * Create a new Atomic distributor with custom step size.
         *
         * @param step The step size for ID segments
         */
        public Atomic(long step) {
            this.step = step;
            this.name = "atomic__" + ATOMIC_COUNTER.incrementAndGet();
        }
        
        /**
         * Get the namespace for this distributor.
         * 
         * @return The namespace
         */
        @Nonnull
        @Override
        public String getNamespace() {
            return "__";
        }
        
        /**
         * Get the name for this distributor.
         * 
         * @return The name
         */
        @Nonnull
        @Override
        public String getName() {
            return name;
        }
        
        /**
         * Get the step size for ID segments.
         * 
         * @return The step size
         */
        @Override
        public long getStep() {
            return step;
        }
        
        /**
         * Allocate the next maximum ID for the specified step size.
         * 
         * @param step The step size for allocation
         * @return The maximum ID in the allocated range
         */
        @Override
        public long nextMaxId(long step) {
            return adder.addAndGet(step);
        }
        
    }
    
    /**
     * Mock implementation of IdSegmentDistributor for testing purposes.
     * 
     * <p>This implementation simulates network I/O delays to test the
     * behavior of ID segment distributors under realistic conditions.
     */
    @VisibleForTesting
    class Mock implements IdSegmentDistributor {
        private static final AtomicInteger MOCK_COUNTER = new AtomicInteger();
        private final long step;
        private final String name;
        private final long ioWaiting;
        private final AtomicLong adder = new AtomicLong();
        
        /**
         * Create a new Mock distributor with default configuration.
         */
        public Mock() {
            this(DEFAULT_STEP, 220000);
        }
        
        /**
         * Create a new Mock distributor with custom configuration.
         *
         * @param step The step size for ID segments
         * @param tps The transactions per second to simulate for I/O waiting
         */
        public Mock(long step, int tps) {
            this.step = step;
            this.ioWaiting = TimeUnit.SECONDS.toNanos(1) / tps;
            this.name = "mock__" + MOCK_COUNTER.incrementAndGet();
        }
        
        /**
         * Get the namespace for this distributor.
         * 
         * @return The namespace
         */
        @Nonnull
        @Override
        public String getNamespace() {
            return "__";
        }
        
        /**
         * Get the name for this distributor.
         * 
         * @return The name
         */
        @Nonnull
        @Override
        public String getName() {
            return name;
        }
        
        /**
         * Get the step size for ID segments.
         * 
         * @return The step size
         */
        @Override
        public long getStep() {
            return step;
        }
        
        /**
         * Allocate the next maximum ID for the specified step size.
         * 
         * <p>This method simulates network I/O delay before allocating the ID.
         *
         * @param step The step size for allocation
         * @return The maximum ID in the allocated range
         */
        @Override
        public long nextMaxId(long step) {
            LockSupport.parkNanos(ioWaiting);
            return adder.addAndGet(step);
        }
        
    }
}
