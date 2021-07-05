package me.ahoo.cosid.segment;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.locks.LockSupport;

/**
 * @author ahoo wang
 */
@Slf4j
public class SegmentChainId implements SegmentId {
    public static final int DEFAULT_SAFE_DISTANCE = 10;
    private final int safeDistance;
    private final IdSegmentDistributor maxIdDistributor;
    private final PrefetchWorker prefetchWorker;
    private volatile IdSegmentClain headClain = new IdSegmentClain(IdSegment.OVERFLOW);

    public SegmentChainId(IdSegmentDistributor maxIdDistributor) {
        this(DEFAULT_SAFE_DISTANCE, maxIdDistributor);
    }

    public SegmentChainId(int safeDistance, IdSegmentDistributor maxIdDistributor) {
        this.safeDistance = safeDistance;
        this.maxIdDistributor = maxIdDistributor;
        this.prefetchWorker = new PrefetchWorker(headClain);
        this.prefetchWorker.start();
    }

    @SneakyThrows
    @Override
    public long generate() {
        if (maxIdDistributor.getStep() == ONE_STEP) {
            return maxIdDistributor.nextMaxId();
        }
        IdSegmentClain currentClain = headClain;
        while (currentClain != null) {
            long nextSeq = currentClain.getIdSegment().getAndIncrement();
            if (nextSeq != IdSegment.SEQUENCE_OVERFLOW) {
                if (currentClain.getVersion() > headClain.getVersion()) {
                    synchronized (this) {
                        if (currentClain.getVersion() > headClain.getVersion()) {
                            headClain = currentClain;
                        }
                    }
                }
                return nextSeq;
            }
            currentClain = currentClain.getNext();
        }

        return generate0();
    }

    private long generate0() {
        synchronized (this) {
            while (true) {
                IdSegmentClain currentClain = headClain;
                while (currentClain != null) {
                    long nextSeq = currentClain.getIdSegment().getAndIncrement();
                    if (nextSeq != IdSegment.SEQUENCE_OVERFLOW) {
                        if (currentClain.getVersion() > headClain.getVersion()) {
                            headClain = currentClain;
                        }
                        return nextSeq;
                    }
                    currentClain = currentClain.getNext();
                }

                final IdSegmentClain preIdSegmentClain = headClain;

                try {
                    IdSegmentClain nextClain = preIdSegmentClain.ensureSetNext(maxIdDistributor::nextIdSegmentClain).getNext();
                    if (log.isDebugEnabled()) {
                        log.debug("generate0 - headClain.version:[{}->{}].", preIdSegmentClain.getVersion(), nextClain.getVersion());
                    }
                } catch (NextIdSegmentExpiredException nextIdSegmentExpiredException) {
                    if (log.isWarnEnabled()) {
                        log.warn("generate0 - gave up this next IdSegmentClain.", nextIdSegmentExpiredException);
                    }
                }
            }
        }
    }


    public class PrefetchWorker extends Thread {
        private volatile long parkPeriod = 4000;
        private volatile IdSegmentClain tailClain;

        PrefetchWorker(IdSegmentClain tailClain) {
            super("CosId-PrefetchWorker");
            this.tailClain = tailClain;
            this.setDaemon(true);
        }

        public IdSegmentClain getTailClain() {
            return tailClain;
        }

        public synchronized void wakeUp() {
            if (log.isDebugEnabled()) {
                log.debug("PrefetchWorker - fastPrefetch - state:[{}].", this.getState());
            }

            if (State.RUNNABLE.equals(this.getState())) {
                return;
            }

            LockSupport.unpark(this);
        }

        public void prefetch() {
            final IdSegmentClain headClain = SegmentChainId.this.headClain;

            final int headToTailGap = headClain.gap(tailClain);
            int safeGap = safeDistance - headToTailGap;

            if (log.isDebugEnabled()) {
                log.debug("PrefetchWorker - prefetch - headClain.version:[{}] - tailClain.version:[{}] - safeGap:[{}].", headClain.getVersion(), tailClain.getVersion(), safeGap);
            }

            if (safeGap <= 0) {
                return;
            }

            for (int i = 0; i < safeGap; i++) {

                try {
                    final IdSegmentClain preTail = tailClain;
                    tailClain = preTail.ensureSetNext(maxIdDistributor::nextIdSegmentClain).getNext();
                    if (log.isDebugEnabled()) {
                        log.debug("PrefetchWorker - restTail - tailClain.version:[{}:{}->{}] - headClain.version:[{}->{}].", preTail.gap(preTail.getNext()), preTail.getVersion(), preTail.getNext().getVersion(), headClain.getVersion(), SegmentChainId.this.headClain.getVersion());
                    }
                } catch (NextIdSegmentExpiredException nextIdSegmentExpiredException) {
                    if (log.isWarnEnabled()) {
                        log.warn("prefetch - gave up this next IdSegmentClain.", nextIdSegmentExpiredException);
                    }
                    safeGap++;
                    continue;
                }
            }
        }

        @Override
        public void run() {
            if (log.isInfoEnabled()) {
                log.info("PrefetchWorker - run.");
            }
            while (!isInterrupted()) {
                prefetch();
                try {
                    LockSupport.parkNanos(parkPeriod);
                } catch (Throwable e) {
                    if (log.isWarnEnabled()) {
                        log.warn(e.getMessage(), e);
                    }
                }
            }
        }
    }
}
