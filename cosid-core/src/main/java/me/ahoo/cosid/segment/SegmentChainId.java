package me.ahoo.cosid.segment;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

/**
 * @author ahoo wang
 */
@Slf4j
public class SegmentChainId implements SegmentId, AutoCloseable {
    public static final int DEFAULT_SAFE_DISTANCE = 10;
    public static final int SPIN_THRESHOLD = 50;
    public static final Duration DEFAULT_PREFETCH_PERIOD = Duration.ofNanos(4000);
    private final int safeDistance;
    private final IdSegmentDistributor maxIdDistributor;
    private final PrefetchWorker prefetchWorker;
    private volatile IdSegmentClain headClain = IdSegmentClain.newRoot();

    public SegmentChainId(IdSegmentDistributor maxIdDistributor) {
        this(DEFAULT_SAFE_DISTANCE, DEFAULT_PREFETCH_PERIOD, maxIdDistributor);
    }

    public SegmentChainId(int safeDistance, Duration prefetchPeriod, IdSegmentDistributor maxIdDistributor) {
        this.safeDistance = safeDistance;
        this.maxIdDistributor = maxIdDistributor;
        this.prefetchWorker = new PrefetchWorker(prefetchPeriod, headClain);
        this.prefetchWorker.start();
    }


    public void stopPrefetchWorker() {
        this.prefetchWorker.shutdown();
    }

    /**
     * No lock, because it is not important, as long as the {@link #headClain} is trending forward.
     * -----
     * synchronized (this) {
     * if (currentClain.getVersion() > headClain.getVersion()) {
     * headClain = currentClain;
     * }
     * }
     *
     * @param forwardClain
     */
    private void forward(IdSegmentClain forwardClain) {
        if (forwardClain.compareTo(headClain) > 0) {
            headClain = forwardClain;
        }
    }


    private IdSegmentClain generateNext(IdSegmentClain previousClain, int segments) {
        return maxIdDistributor.nextIdSegmentClain(previousClain, segments);
    }

    private void checkClain(IdSegmentClain currentClain) {
        if (DefaultIdSegment.OVERFLOW.equals(currentClain.getIdSegment())) {
            currentClain = currentClain.getNext();
        }
        while (currentClain != null && currentClain.getNext() != null) {
            IdSegmentClain nextNextClain = currentClain.getNext();
            if (currentClain.compareTo(nextNextClain) >= 0) {
                throw new NextIdSegmentExpiredException(currentClain, nextNextClain);
            }

            if (currentClain.getVersion() + 1 != nextNextClain.getVersion()) {
                throw new NextIdSegmentExpiredException(currentClain, nextNextClain);
            }

            if (currentClain.getIdSegment().getOffset() + maxIdDistributor.getStep() > nextNextClain.getIdSegment().getOffset()) {
                throw new NextIdSegmentExpiredException(currentClain, nextNextClain);
            }

            currentClain = nextNextClain;
        }
    }

    @SneakyThrows
    @Override
    public long generate() {
        while (true) {
            IdSegmentClain currentClain = headClain;
            while (currentClain != null) {
                long nextSeq = currentClain.incrementAndGet();
                if (nextSeq != DefaultIdSegment.SEQUENCE_OVERFLOW) {
                    forward(currentClain);
                    return nextSeq;
                }
                currentClain = currentClain.getNext();
            }

            try {
                final IdSegmentClain preIdSegmentClain = headClain;
                IdSegmentClain nextClain = preIdSegmentClain.ensureSetNext((preClain) -> generateNext(preClain, safeDistance)).getNext();
//                checkClain(preIdSegmentClain);
                if (log.isDebugEnabled()) {
                    log.debug("generate - headClain.version:[{}->{}].", preIdSegmentClain.getVersion(), nextClain.getVersion());
                }
            } catch (NextIdSegmentExpiredException nextIdSegmentExpiredException) {
                if (log.isWarnEnabled()) {
                    log.warn("generate - gave up this next IdSegmentClain.", nextIdSegmentExpiredException);
                }
            }
        }
    }

    /**
     * Closes this resource, relinquishing any underlying resources.
     * This method is invoked automatically on objects managed by the
     * {@code try}-with-resources statement.
     *
     * <p>While this interface method is declared to throw {@code
     * Exception}, implementers are <em>strongly</em> encouraged to
     * declare concrete implementations of the {@code close} method to
     * throw more specific exceptions, or to throw no exception at all
     * if the close operation cannot fail.
     *
     * <p> Cases where the close operation may fail require careful
     * attention by implementers. It is strongly advised to relinquish
     * the underlying resources and to internally <em>mark</em> the
     * resource as closed, prior to throwing the exception. The {@code
     * close} method is unlikely to be invoked more than once and so
     * this ensures that the resources are released in a timely manner.
     * Furthermore it reduces problems that could arise when the resource
     * wraps, or is wrapped, by another resource.
     *
     * <p><em>Implementers of this interface are also strongly advised
     * to not have the {@code close} method throw {@link
     * InterruptedException}.</em>
     * <p>
     * This exception interacts with a thread's interrupted status,
     * and runtime misbehavior is likely to occur if an {@code
     * InterruptedException} is {@linkplain Throwable#addSuppressed
     * suppressed}.
     * <p>
     * More generally, if it would cause problems for an
     * exception to be suppressed, the {@code AutoCloseable.close}
     * method should not throw it.
     *
     * <p>Note that unlike the {@link Closeable#close close}
     * method of {@link Closeable}, this {@code close} method
     * is <em>not</em> required to be idempotent.  In other words,
     * calling this {@code close} method more than once may have some
     * visible side effect, unlike {@code Closeable.close} which is
     * required to have no effect if called more than once.
     * <p>
     * However, implementers of this interface are strongly encouraged
     * to make their {@code close} methods idempotent.
     *
     * @throws Exception if this resource cannot be closed
     */
    @Override
    public void close() throws Exception {
        this.stopPrefetchWorker();
    }

    public class PrefetchWorker extends Thread {
        private final Duration prefetchPeriod;
        private IdSegmentClain tailClain;
        private volatile boolean stopped = false;

        PrefetchWorker(Duration prefetchPeriod, IdSegmentClain tailClain) {
            super("CosId-PrefetchWorker");
            this.prefetchPeriod = prefetchPeriod;
            this.tailClain = tailClain;
            this.setDaemon(true);
        }

        public synchronized void wakeUp() {
            if (log.isDebugEnabled()) {
                log.debug("PrefetchWorker - wakeUp - state:[{}].", this.getState());
            }

            if (State.RUNNABLE.equals(this.getState())) {
                return;
            }

            LockSupport.unpark(this);
        }

        public void prefetch() {

            IdSegmentClain usableHeadClain = SegmentChainId.this.headClain;
            while (usableHeadClain.getIdSegment().isOverflow()) {
                usableHeadClain = usableHeadClain.getNext();
                if (usableHeadClain == null) {
                    usableHeadClain = tailClain;
                    break;
                }
            }

            forward(usableHeadClain);
            final int headToTailGap = usableHeadClain.gap(tailClain);

            final int safeGap = safeDistance - headToTailGap;

            if (log.isDebugEnabled()) {
                log.debug("PrefetchWorker - prefetch - headClain.version:[{}] - tailClain.version:[{}] - safeGap:[{}].", usableHeadClain.getVersion(), tailClain.getVersion(), safeGap);
            }

            if (safeGap <= 0) {
                return;
            }

            try {
                final IdSegmentClain preTail = tailClain;
                tailClain = tailClain.ensureSetNext((preClain) -> generateNext(preClain, safeGap)).getNext();
//                checkClain(preTail);
                while (tailClain.getNext() != null) {
                    tailClain = tailClain.getNext();
                }
                if (log.isDebugEnabled()) {
                    log.debug("PrefetchWorker - restTail - tailClain.version:[{}:{}->{}] - headClain.version:[{}->{}].", preTail.gap(preTail.getNext()), preTail.getVersion(), preTail.getNext().getVersion(), headClain.getVersion(), SegmentChainId.this.headClain.getVersion());
                }
            } catch (NextIdSegmentExpiredException nextIdSegmentExpiredException) {
                if (log.isWarnEnabled()) {
                    log.warn("prefetch - gave up this next IdSegmentClain.", nextIdSegmentExpiredException);
                }
            }
        }

        public void shutdown() {
            if (log.isInfoEnabled()) {
                log.info("PrefetchWorker - shutdown!");
            }
            stopped = true;
        }

        @Override
        public void run() {
            if (log.isInfoEnabled()) {
                log.info("PrefetchWorker - run.");
            }
            while (!stopped && !isInterrupted()) {
                try {
                    LockSupport.parkNanos(prefetchPeriod.toNanos());
                    prefetch();
                } catch (Throwable throwable) {
                    if (log.isErrorEnabled()) {
                        log.error(throwable.getMessage(), throwable);
                    }
                }
            }
        }
    }
}
