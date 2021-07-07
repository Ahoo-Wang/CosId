package me.ahoo.cosid.segment;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ahoo wang
 */
public class DefaultIdSegment implements IdSegment {

    public static final DefaultIdSegment OVERFLOW = new DefaultIdSegment(-1, 0);

    /**
     * include
     */
    private final long maxId;
    private final long offset;
    private final int step;
    private final AtomicLong sequence;

    public DefaultIdSegment(long maxId, int step) {
        this.maxId = maxId;
        this.step = step;
        this.offset = maxId - step;
        this.sequence = new AtomicLong(offset);
    }

    @Override
    public long getMaxId() {
        return maxId;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public long getSequence() {
        return sequence.get();
    }

    @Override
    public int getStep() {
        return step;
    }

    @Override
    public long incrementAndGet() {
        if (isOverflow()) {
            return SEQUENCE_OVERFLOW;
        }

        final long nextSeq = sequence.incrementAndGet();

        if (isOverflow(nextSeq)) {
            return SEQUENCE_OVERFLOW;
        }
        return nextSeq;
    }

    @Override
    public String toString() {
        return "DefaultIdSegment{" +
                "maxId=" + maxId +
                ", offset=" + offset +
                ", step=" + step +
                ", sequence=" + sequence +
                '}';
    }
}
