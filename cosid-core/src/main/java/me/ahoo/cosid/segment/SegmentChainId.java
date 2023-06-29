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
 * <p><img src="../doc-files/SegmentChainId.png" alt="SegmentChainId"></p>
 *
 * @author ahoo wang
 */
@Slf4j
public class SegmentChainId implements SegmentId {
    public static final int DEFAULT_SAFE_DISTANCE = 2;
    
    private final long idSegmentTtl;
    private final int safeDistance;
    private final IdSegmentDistributor maxIdDistributor;
    private final PrefetchJob prefetchJob;
    private volatile IdSegmentChain headChain = IdSegmentChain.newRoot();
    
    public SegmentChainId(IdSegmentDistributor maxIdDistributor) {
        this(TIME_TO_LIVE_FOREVER, DEFAULT_SAFE_DISTANCE, maxIdDistributor, PrefetchWorkerExecutorService.DEFAULT);
    }
    
    public SegmentChainId(long idSegmentTtl, int safeDistance, IdSegmentDistributor maxIdDistributor, PrefetchWorkerExecutorService prefetchWorkerExecutorService) {
        Preconditions.checkArgument(idSegmentTtl > 0, Strings.lenientFormat("Illegal idSegmentTtl parameter:[%s].", idSegmentTtl));
        Preconditions.checkArgument(safeDistance > 0, "The safety distance must be greater than 0.");
        this.idSegmentTtl = idSegmentTtl;
        this.safeDistance = safeDistance;
        this.maxIdDistributor = maxIdDistributor;
        prefetchJob = new PrefetchJob(headChain);
        prefetchWorkerExecutorService.submit(prefetchJob);
    }
    
    public IdSegmentChain getHead() {
        return headChain;
    }
    
    /**
     * No lock, because it is not important, as long as the {@link #headChain} is trending forward.
     * -----
     * <pre>
     * synchronized (this) {
     *   if (forwardChain.getVersion() > headChain.getVersion()) {
     *      headChain = forwardChain;
     *  }
     * }
     * </pre>
     *
     * @param forwardChain forward IdSegmentChain
     */
    private void forward(IdSegmentChain forwardChain) {
        if (headChain.getVersion() >= forwardChain.getVersion()) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("Forward [{}] - [{}] -> [{}].", maxIdDistributor.getNamespacedName(), headChain, forwardChain);
        }
        if (forwardChain.allowReset()) {
            headChain = forwardChain;
        } else if (forwardChain.compareTo(headChain) > 0) {
            headChain = forwardChain;
        }
        
    }
    
    private IdSegmentChain generateNext(IdSegmentChain previousChain, int segments) {
        return maxIdDistributor.nextIdSegmentChain(previousChain, segments, idSegmentTtl);
    }
    
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
    
    public class PrefetchJob implements AffinityJob {
        private static final int MAX_PREFETCH_DISTANCE = 100_000_000;
        /**
         * Duration.ofSeconds(5);
         */
        private static final long hungerThreshold = 5;
        private volatile PrefetchWorker prefetchWorker;
        private int prefetchDistance = safeDistance;
        private IdSegmentChain tailChain;
        /**
         * last Hunger Time.
         *
         * @see java.util.concurrent.TimeUnit#SECONDS
         */
        private volatile long lastHungerTime;
        
        public PrefetchJob(IdSegmentChain tailChain) {
            this.tailChain = tailChain;
        }
        
        @Override
        public String getJobId() {
            return maxIdDistributor.getNamespacedName();
        }
        
        @Override
        public void setHungerTime(long hungerTime) {
            lastHungerTime = hungerTime;
        }
        
        @Override
        public PrefetchWorker getPrefetchWorker() {
            return prefetchWorker;
        }
        
        @Override
        public void setPrefetchWorker(PrefetchWorker prefetchWorker) {
            if (this.prefetchWorker != null) {
                return;
            }
            this.prefetchWorker = prefetchWorker;
        }
        
        @Override
        public void run() {
            prefetch();
        }
        
        public void prefetch() {
            
            long wakeupTimeGap = Clock.CACHE.secondTime() - lastHungerTime;
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
