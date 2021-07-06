package me.ahoo.cosid.segment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ahoo wang
 */
public interface IdSegmentDistributor {
    int DEFAULT_SEGMENTS = 1;
    int DEFAULT_STEP = 100;

    int getStep();

    default int getStep(int segments) {
        return getStep() * segments;
    }

    long nextMaxId(int step);

    default long nextMaxId() {
        return nextMaxId(getStep());
    }

    default List<IdSegment> nextIdSegment(int segments) {
        final int totalStep = getStep(segments);
        final long maxId = nextMaxId(totalStep);
        final long offset = maxId - totalStep;
        List<IdSegment> idSegments = new ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            long currentMaxId = offset + getStep() * (i + 1);
            DefaultIdSegment segment = new DefaultIdSegment(currentMaxId, getStep());
            idSegments.add(segment);
        }
        return idSegments;
    }

    default IdSegment nextIdSegment() {
        long maxId = nextMaxId();
        return new DefaultIdSegment(maxId, getStep());
    }

    default IdSegmentClain nextIdSegmentClain(IdSegmentClain previousClain) {
        IdSegment nextIdSegment = nextIdSegment();
        return new IdSegmentClain(previousClain, nextIdSegment);
    }

    default IdSegmentClain nextIdSegmentClain(IdSegmentClain previousClain, int segments) {
        if (DEFAULT_SEGMENTS == segments) {
            return nextIdSegmentClain(previousClain);
        }
        List<IdSegment> nextIdSegments = nextIdSegment(segments);
        IdSegmentClain rootClain = null;
        IdSegmentClain currentClain = null;
        for (IdSegment nextIdSegment : nextIdSegments) {
            if (Objects.isNull(rootClain)) {
                rootClain = new IdSegmentClain(previousClain, nextIdSegment);
                currentClain = rootClain;
                continue;
            }
            currentClain.setNext(new IdSegmentClain(currentClain, nextIdSegment));
            currentClain = currentClain.getNext();
        }
        return rootClain;
    }

    class JdkIdSegmentDistributor implements IdSegmentDistributor {

        private final AtomicLong adder = new AtomicLong();

        @Override
        public int getStep() {
            return DEFAULT_STEP;
        }

        @Override
        public long nextMaxId(int step) {
            return adder.addAndGet(step);
        }

    }
}
