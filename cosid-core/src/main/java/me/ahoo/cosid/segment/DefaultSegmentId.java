package me.ahoo.cosid.segment;

import lombok.extern.slf4j.Slf4j;

/**
 * @author ahoo wang
 */
@Slf4j
public class DefaultSegmentId implements SegmentId {

    private final IdSegmentDistributor maxIdDistributor;
    
    private volatile IdSegment segment = IdSegment.OVERFLOW;

    public DefaultSegmentId(IdSegmentDistributor maxIdDistributor) {
        this.maxIdDistributor = maxIdDistributor;
    }

    @Override
    public long generate() {

        if (maxIdDistributor.getStep() == ONE_STEP) {
            return maxIdDistributor.nextMaxId();
        }

        long nextSeq = segment.getAndIncrement();
        if (nextSeq != IdSegment.SEQUENCE_OVERFLOW) {
            return nextSeq;
        }

        synchronized (this) {
            while (true) {
                nextSeq = segment.getAndIncrement();
                if (nextSeq != IdSegment.SEQUENCE_OVERFLOW) {
                    return nextSeq;
                }
                segment = maxIdDistributor.nextIdSegment();
            }
        }
    }

}
