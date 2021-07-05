package me.ahoo.cosid.segment;

import java.util.function.Supplier;

/**
 * @author ahoo wang
 */
public class IdSegmentClain {
    public static final IdSegmentClain NOT_SET = null;
    private volatile int version;
    private final IdSegment idSegment;
    private volatile IdSegmentClain next;

    public IdSegmentClain(IdSegment idSegment) {
        this.idSegment = idSegment;
    }

    public boolean trySetNext(Supplier<IdSegmentClain> idSegmentClainSupplier) throws NextIdSegmentExpiredException {
        if (NOT_SET != next) {
            return false;
        }

        synchronized (this) {
            if (NOT_SET != next) {
                return false;
            }
            IdSegmentClain nextIdSegmentClain = idSegmentClainSupplier.get();
            if (nextIdSegmentClain.getIdSegment().getOffset() < idSegment.getOffset()) {
                throw new NextIdSegmentExpiredException(this, nextIdSegmentClain);
            }
            next = nextIdSegmentClain;
            nextIdSegmentClain.version = version + 1;
            return true;
        }
    }

    public IdSegmentClain ensureSetNext(Supplier<IdSegmentClain> idSegmentClainSupplier) throws NextIdSegmentExpiredException {
        IdSegmentClain currentClain = this;
        while (!currentClain.trySetNext(idSegmentClainSupplier)) {
            currentClain = currentClain.getNext();
        }
        return currentClain;
    }

    public IdSegmentClain getNext() {
        return next;
    }

    public IdSegment getIdSegment() {
        return idSegment;
    }

    public int getVersion() {
        return version;
    }

    public int gap(IdSegmentClain end) {
        return end.version - version;
    }
}
