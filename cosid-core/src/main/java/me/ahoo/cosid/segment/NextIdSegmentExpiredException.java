package me.ahoo.cosid.segment;

import com.google.common.base.Strings;
import me.ahoo.cosid.CosIdException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ahoo wang
 */
public class NextIdSegmentExpiredException extends CosIdException {
    private static final AtomicLong times = new AtomicLong(0);
    private final IdSegmentClain current;
    private final IdSegmentClain next;

    public NextIdSegmentExpiredException(IdSegmentClain current, IdSegmentClain next) {
        super(Strings.lenientFormat("next offset:[%s] cannot be less than current:[%s] -- times:[%s].",
                next.getIdSegment().getOffset(),
                current.getIdSegment().getOffset(),
                times.incrementAndGet())
        );
        this.current = current;
        this.next = next;
    }

    public IdSegmentClain getCurrent() {
        return current;
    }

    public IdSegmentClain getNext() {
        return next;
    }
}
