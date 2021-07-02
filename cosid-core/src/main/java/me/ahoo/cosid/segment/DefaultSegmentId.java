package me.ahoo.cosid.segment;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@Slf4j
public class DefaultSegmentId implements SegmentId {

    private static final int ONE = 1;

    private final IdSegmentDistributor maxIdDistributor;
    private volatile IdSegment segment;

    public DefaultSegmentId(IdSegmentDistributor maxIdDistributor) {
        this.maxIdDistributor = maxIdDistributor;
    }

    @Override
    public long generate() {

        if (maxIdDistributor.getStep() == ONE) {
            return maxIdDistributor.nextMaxId();
        }

        if (Objects.nonNull(segment)) {
            long nextSeq = segment.getAndIncrement();
            if (nextSeq != IdSegment.SEQUENCE_OVERFLOW) {
                return nextSeq;
            }
        }

        synchronized (this) {
            while (true) {
                if (Objects.nonNull(segment)) {
                    long nextSeq = segment.getAndIncrement();
                    if (nextSeq != IdSegment.SEQUENCE_OVERFLOW) {
                        return nextSeq;
                    }
                }
                segment = maxIdDistributor.nextIdSegment();
            }
        }
    }

}
