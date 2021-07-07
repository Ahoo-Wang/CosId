package me.ahoo.cosid.segment;

import lombok.extern.slf4j.Slf4j;

/**
 * @author ahoo wang
 */
@Slf4j
public class DefaultSegmentId implements SegmentId {

    private final IdSegmentDistributor maxIdDistributor;

    private volatile IdSegment segment = DefaultIdSegment.OVERFLOW;

    public DefaultSegmentId(IdSegmentDistributor maxIdDistributor) {
        this.maxIdDistributor = maxIdDistributor;
    }

    @Override
    public long generate() {

        if (maxIdDistributor.getStep() == ONE_STEP) {
            return maxIdDistributor.nextMaxId();
        }

        long nextSeq = segment.incrementAndGet();
        if (!segment.isOverflow(nextSeq)){
            return nextSeq;
        }

        synchronized (this) {
            while (true) {
                nextSeq = segment.incrementAndGet();
                if(!segment.isOverflow(nextSeq)){
                    return nextSeq;
                }
                IdSegment nextIdSegment = maxIdDistributor.nextIdSegment();
                segment.ensureNextIdSegment(nextIdSegment);
                segment = nextIdSegment;
            }
        }
    }

}
