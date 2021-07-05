package me.ahoo.cosid.segment;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ahoo wang
 */
public interface IdSegmentDistributor {

    int DEFAULT_STEP = 100;

    int getStep();

    long nextMaxId();

    default IdSegment nextIdSegment() {
        long maxId = nextMaxId();
        return new IdSegment(maxId, getStep());
    }

    default IdSegmentClain nextIdSegmentClain() {
        IdSegment nextIdSegment = nextIdSegment();
        return new IdSegmentClain(nextIdSegment);
    }

    class JdkIdSegmentDistributor implements IdSegmentDistributor {

        private final AtomicLong adder = new AtomicLong();

        @Override
        public int getStep() {
            return DEFAULT_STEP;
        }

        @Override
        public long nextMaxId() {
            return adder.addAndGet(DEFAULT_STEP);
        }

    }
}
