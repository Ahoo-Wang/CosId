package me.ahoo.cosid.segment;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ahoo wang
 */
public class IdSegment {
    public static final IdSegment OVERFLOW = new IdSegment(0, 0);

    public static final long SEQUENCE_OVERFLOW = -1;
    /**
     * include
     */
    private final long maxId;
    private final int step;
    private final AtomicLong sequence;

    public IdSegment(long maxId, int step) {
        this.maxId = maxId;
        this.step = step;
        this.sequence = new AtomicLong(maxId - step);
    }

    public long getMaxId() {
        return maxId;
    }

    public long getSequence() {
        return sequence.get();
    }

    public int getStep() {
        return step;
    }

    public long getAndIncrement() {
        if (sequence.get() >= maxId) {
            return SEQUENCE_OVERFLOW;
        }

        final long nextSeq = sequence.incrementAndGet();
        if (nextSeq > maxId) {
            return SEQUENCE_OVERFLOW;
        }
        return nextSeq;
    }

    @Override
    public String toString() {
        return "Segment{" +
                "maxId=" + maxId +
                ", sequence=" + sequence +
                '}';
    }
}
