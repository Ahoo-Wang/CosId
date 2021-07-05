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
    private final long offset;
    private final int step;
    private final AtomicLong sequence;

    public IdSegment(long maxId, int step) {
        this.maxId = maxId;
        this.step = step;
        this.offset = maxId - step;
        this.sequence = new AtomicLong(offset);
    }

    public long getMaxId() {
        return maxId;
    }

    public long getOffset() {
        return offset;
    }

    public long getSequence() {
        return sequence.get();
    }

    public int getStep() {
        return step;
    }

    public boolean isOverflow() {
        return sequence.get() >= maxId;
    }

    public long getAndIncrement() {
        if (isOverflow()) {
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
        return "IdSegment{" +
                "maxId=" + maxId +
                ", offset=" + offset +
                ", step=" + step +
                ", sequence=" + sequence +
                '}';
    }
}
