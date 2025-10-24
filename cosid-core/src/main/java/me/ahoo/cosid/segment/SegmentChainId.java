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

import me.ahoo.cosid.segment.concurrent.AffinityJob;
import me.ahoo.cosid.segment.concurrent.PrefetchWorker;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.util.Clock;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;

/**
 * Segment chain algorithm ID generator.
 *
 * <p>This implementation extends the basic segment algorithm by organizing
 * segments into a chain structure, providing improved performance and
 * resilience through prefetching and dynamic scaling.
 *
 * <p>Key features of the segment chain algorithm:
 * <ul>
 *   <li><strong>Chain Structure</strong>: Segments are organized in a linked chain</li>
 *   <li><strong>Prefetching</strong>: Segments are prefetched in the background</li>
 *   <li><strong>Dynamic Scaling</strong>: Prefetch distance adapts to demand</li>
 *   <li><strong>High Availability</strong>: Multiple segments provide resilience</li>
 * </ul>
 *
 * <p>The algorithm works by:
 * <ol>
 *   <li>Maintaining a chain of ID segments</li>
 *   <li>Generating IDs from the head of the chain</li>
 *   <li>Prefetching new segments in the background</li>
 *   <li>Dynamically adjusting prefetch distance based on demand</li>
 * </ol>
 *
 * <p><img src="../doc-files/SegmentChainId.png" alt="SegmentChainId"></p>
 *
 * @author ahoo wang
 */
@Slf4j
public class SegmentChainId implements SegmentId {
    /**
     * The default safe distance for prefetching segments.
     *
     * <p>This is the minimum number of segments to keep prefetched ahead
     * of the current consumption point to ensure continuous ID generation.
     */
    public static final int DEFAULT_SAFE_DISTANCE = 2;

    /**
     * The time-to-live for ID segments in milliseconds.
     *
     * <p>This determines how long segments remain valid before they should
     * be refreshed or replaced.
     */
    private final long idSegmentTtl;

    /**
     * The safe distance for prefetching segments.
     *
     * <p>This determines how many segments ahead to prefetch to ensure
     * continuous ID generation even under high demand.
     */
    private final int safeDistance;

    /**
     * The distributor used to allocate new segments.
     *
     * <p>This component is responsible for coordinating segment allocation
     * across distributed instances to ensure global uniqueness.
     */
    private final IdSegmentDistributor maxIdDistributor;

    /**
     * The prefetch job responsible for background segment prefetching.
     *
     * <p>This job runs in the background to prefetch segments before they
     * are needed, reducing latency during ID generation.
     */
    private final PrefetchJob prefetchJob;

    /**
     * The head of the segment chain.
     *
     * <p>This is the current segment from which IDs are being generated.
     * It is marked volatile to ensure visibility across threads.
     */
    private volatile IdSegmentChain headChain;

    /**
     * Create a new SegmentChainId with default configuration.
     *
     * <p>This constructor creates a generator with infinite segment TTL,
     * default safe distance, and the default prefetch worker executor.
     *
     * @param maxIdDistributor The distributor for allocating new segments
     */
    public SegmentChainId(IdSegmentDistributor maxIdDistributor) {
        this(TIME_TO_LIVE_FOREVER, DEFAULT_SAFE_DISTANCE, maxIdDistributor, PrefetchWorkerExecutorService.DEFAULT);
    }

    /**
     * Create a new SegmentChainId with custom configuration.
     *
     * <p>This constructor allows full control over all parameters of the
     * segment chain ID generator.
     *
     * @param idSegmentTtl                  The time-to-live for segments
     * @param safeDistance                  The safe distance for prefetching
     * @param maxIdDistributor              The distributor for allocating new segments
     * @param prefetchWorkerExecutorService The executor for prefetch jobs
     */
    public SegmentChainId(long idSegmentTtl, int safeDistance, IdSegmentDistributor maxIdDistributor, PrefetchWorkerExecutorService prefetchWorkerExecutorService) {
        Preconditions.checkArgument(idSegmentTtl > 0, Strings.lenientFormat("Illegal idSegmentTtl parameter:[%s].", idSegmentTtl));
        Preconditions.checkArgument(safeDistance > 0, "The safety distance must be greater than 0.");
        this.headChain = IdSegmentChain.newRoot(maxIdDistributor.allowReset());
        this.idSegmentTtl = idSegmentTtl;
        this.safeDistance = safeDistance;
        this.maxIdDistributor = maxIdDistributor;
        prefetchJob = new PrefetchJob(headChain);
        prefetchWorkerExecutorService.submit(prefetchJob);
    }

    /**
     * Get the current ID segment (the head of the chain).
     *
     * <p>This method returns the segment from which IDs are currently being
     * allocated. In the chain implementation, this is the head of the segment chain.
     *
     * @return The current ID segment (head of the chain)
     */
    @Override
    public IdSegment current() {
        return headChain;
    }

    /**
     * Get the head of the segment chain.
     *
     * <p>This method provides direct access to the head of the segment chain,
     * which contains additional chain-specific information beyond the basic
     * segment interface.
     *
     * @return The head of the segment chain
     */
    public IdSegmentChain getHead() {
        return headChain;
    }

    /**
     * Forward the head of the chain to a newer segment.
     *
     * <p>This method updates the head of the chain to point to a newer segment,
     * ensuring that ID generation progresses through the chain in the correct order.
     *
     * <p>No lock is used because it's not critical for correctness as long as
     * the head chain is moving forward in the proper direction.
     *
     * @param forwardChain The new head of the chain
     */
    private void forward(IdSegmentChain forwardChain) {
        if (headChain.getVersion() >= forwardChain.getVersion()) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("Forward [{}] - [{}] -> [{}].", maxIdDistributor.getNamespacedName(), headChain, forwardChain);
        }
        if (maxIdDistributor.allowReset()) {
            headChain = forwardChain;
        } else if (forwardChain.compareTo(headChain) > 0) {
            headChain = forwardChain;
        }

    }

    /**
     * Generate the next segment chain in the sequence.
     *
     * <p>This method requests a new segment chain from the distributor to
     * extend the current chain.
     *
     * @param previousChain The previous chain segment
     * @param segments      The number of segments to generate
     * @return The next segment chain
     */
    private IdSegmentChain generateNext(IdSegmentChain previousChain, int segments) {
        return maxIdDistributor.nextIdSegmentChain(previousChain, segments, idSegmentTtl);
    }

    /**
     * Generate a new distributed ID.
     *
     * <p>This method generates a unique ID by traversing the segment chain
     * and allocating an ID from the first available segment. If no segments
     * are available, it triggers prefetching of new segments.
     *
     * @return A unique distributed ID
     */
    @Override
    public long generate() {
        while (true) {
            IdSegmentChain currentChain = headChain;
            while (currentChain != null) {
                if (currentChain.isAvailable()) {
                    long nextSeq = currentChain.incrementAndGet();
                    if (!currentChain.isOverflow(nextSeq)) {
                        forward(currentChain);
                        return nextSeq;
                    }
                }
                currentChain = currentChain.getNext();
            }

            try {
                final IdSegmentChain preIdSegmentChain = headChain;

                if (preIdSegmentChain.trySetNext((preChain) -> generateNext(preChain, safeDistance))) {
                    IdSegmentChain nextChain = preIdSegmentChain.getNext();
                    forward(nextChain);
                    if (log.isDebugEnabled()) {
                        log.debug("Generate [{}] - headChain.version:[{}->{}].", maxIdDistributor.getNamespacedName(), preIdSegmentChain.getVersion(), nextChain.getVersion());
                    }
                }
            } catch (NextIdSegmentExpiredException nextIdSegmentExpiredException) {
                if (log.isWarnEnabled()) {
                    log.warn("Generate [{}] - gave up this next IdSegmentChain.", maxIdDistributor.getNamespacedName(), nextIdSegmentExpiredException);
                }
            }
            this.prefetchJob.hungry();
        }
    }

    /**
     * Background prefetch job for segment chain ID generation.
     *
     * <p>This job runs in the background to prefetch segments before they
     * are needed, reducing latency during ID generation. It dynamically
     * adjusts the prefetch distance based on demand patterns.
     */
    public class PrefetchJob implements AffinityJob {
        /**
         * The maximum prefetch distance to prevent excessive resource usage.
         */
        private static final int MAX_PREFETCH_DISTANCE = 100_000_000;

        /**
         * The hunger threshold in seconds.
         *
         * <p>If the time since the last hunger signal is less than this
         * threshold, the system is considered to be under high demand.
         */
        private static final long hungerThreshold = 5;

        /**
         * The prefetch worker that executes this job.
         */
        private volatile PrefetchWorker prefetchWorker;

        /**
         * The current prefetch distance.
         *
         * <p>This value is dynamically adjusted based on demand patterns
         * to optimize performance.
         */
        private int prefetchDistance = safeDistance;

        /**
         * The tail of the segment chain.
         *
         * <p>This represents the end of the prefetched segment chain.
         */
        private IdSegmentChain tailChain;

        /**
         * The last hunger time in seconds.
         *
         * <p>This timestamp is used to determine demand patterns and
         * adjust the prefetch distance accordingly.
         *
         * @see java.util.concurrent.TimeUnit#SECONDS
         */
        private volatile long lastHungerTime;

        /**
         * Create a new prefetch job for the specified tail chain.
         *
         * @param tailChain The tail of the segment chain
         */
        public PrefetchJob(IdSegmentChain tailChain) {
            this.tailChain = tailChain;
        }

        /**
         * Get the job ID for this prefetch job.
         *
         * <p>The job ID is used to identify this job in the prefetch worker system.
         *
         * @return The job ID
         */
        @Override
        public String getJobId() {
            return maxIdDistributor.getNamespacedName();
        }

        /**
         * Set the hunger time for this job.
         *
         * <p>This method is called when the system signals high demand,
         * indicating that prefetching should be more aggressive.
         *
         * @param hungerTime The hunger time in seconds
         */
        @Override
        public void setHungerTime(long hungerTime) {
            lastHungerTime = hungerTime;
        }

        /**
         * Get the prefetch worker for this job.
         *
         * @return The prefetch worker
         */
        @Override
        public PrefetchWorker getPrefetchWorker() {
            return prefetchWorker;
        }

        /**
         * Set the prefetch worker for this job.
         *
         * <p>This method is called by the prefetch worker executor when
         * assigning a worker to this job.
         *
         * @param prefetchWorker The prefetch worker
         */
        @Override
        public void setPrefetchWorker(PrefetchWorker prefetchWorker) {
            if (this.prefetchWorker != null) {
                return;
            }
            this.prefetchWorker = prefetchWorker;
        }

        /**
         * Run the prefetch job.
         *
         * <p>This method is called by the prefetch worker to execute
         * the prefetching logic.
         */
        @Override
        public void run() {
            prefetch();
        }

        /**
         * Perform the prefetching logic.
         *
         * <p>This method implements the core prefetching algorithm, which:
         * <ol>
         *   <li>Determines current demand based on hunger signals</li>
         *   <li>Adjusts prefetch distance based on demand</li>
         *   <li>Identifies the available head of the chain</li>
         *   <li>Calculates the gap to the tail</li>
         *   <li>Prefetches segments if needed</li>
         * </ol>
         */
        public void prefetch() {

            long wakeupTimeGap = Clock.SYSTEM.secondTime() - lastHungerTime;
            final boolean hunger = wakeupTimeGap < hungerThreshold;

            final int prePrefetchDistance = this.prefetchDistance;
            if (hunger) {
                this.prefetchDistance = Math.min(Math.multiplyExact(this.prefetchDistance, 2), MAX_PREFETCH_DISTANCE);
                if (log.isInfoEnabled()) {
                    log.info("Prefetch [{}] - Hunger, Safety distance expansion.[{}->{}]", maxIdDistributor.getNamespacedName(), prePrefetchDistance, this.prefetchDistance);
                }
            } else {
                this.prefetchDistance = Math.max(Math.floorDiv(this.prefetchDistance, 2), safeDistance);
                if (prePrefetchDistance > this.prefetchDistance) {
                    if (log.isInfoEnabled()) {
                        log.info("Prefetch [{}] - Full, Safety distance shrinks.[{}->{}]", maxIdDistributor.getNamespacedName(), prePrefetchDistance, this.prefetchDistance);
                    }
                }
            }

            IdSegmentChain availableHeadChain = SegmentChainId.this.headChain;
            while (!availableHeadChain.getIdSegment().isAvailable()) {
                availableHeadChain = availableHeadChain.getNext();
                if (availableHeadChain == null) {
                    availableHeadChain = tailChain;
                    break;
                }
            }

            forward(availableHeadChain);

            final int headToTailGap = availableHeadChain.gap(tailChain, maxIdDistributor.getStep());
            final int safeGap = safeDistance - headToTailGap;

            if (safeGap <= 0 && !hunger) {
                if (log.isTraceEnabled()) {
                    log.trace("Prefetch [{}] - safeGap is less than or equal to 0, and is not hungry - headChain.version:[{}] - tailChain.version:[{}].", maxIdDistributor.getNamespacedName(),
                        availableHeadChain.getVersion(), tailChain.getVersion());
                }
                return;
            }

            final int prefetchSegments = hunger ? this.prefetchDistance : safeGap;

            appendChain(availableHeadChain, prefetchSegments);
        }

        /**
         * Append new segments to the chain.
         *
         * <p>This method extends the segment chain by requesting new segments
         * from the distributor and appending them to the tail.
         *
         * @param availableHeadChain The current head of the available chain
         * @param prefetchSegments   The number of segments to prefetch
         */
        private void appendChain(IdSegmentChain availableHeadChain, int prefetchSegments) {

            if (log.isDebugEnabled()) {
                log.debug("AppendChain [{}] - headChain.version:[{}] - tailChain.version:[{}] - prefetchSegments:[{}].", maxIdDistributor.getNamespacedName(), availableHeadChain.getVersion(),
                    tailChain.getVersion(), prefetchSegments);
            }

            try {
                final IdSegmentChain preTail = tailChain;
                tailChain = tailChain.ensureSetNext((preChain) -> generateNext(preChain, prefetchSegments)).getNext();
                while (tailChain.getNext() != null) {
                    tailChain = tailChain.getNext();
                }
                if (log.isDebugEnabled()) {
                    log.debug("AppendChain [{}] - restTail - tailChain.version:[{}:{}->{}] .", maxIdDistributor.getNamespacedName(), preTail.gap(tailChain, maxIdDistributor.getStep()),
                        preTail.getVersion(), tailChain.getVersion());
                }
            } catch (NextIdSegmentExpiredException nextIdSegmentExpiredException) {
                if (log.isWarnEnabled()) {
                    log.warn("AppendChain [{}] - gave up this next IdSegmentChain.", maxIdDistributor.getNamespacedName(), nextIdSegmentExpiredException);
                }
            }
        }
    }
}
