package me.ahoo.cosid.segment;

import com.google.common.base.Strings;
import me.ahoo.cosid.CosIdException;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author ahoo wang
 */
public class NextIdSegmentExpiredException extends CosIdException {
    private static final AtomicLong times = new AtomicLong(0);
    private final IdSegment current;
    private final IdSegment next;

    public NextIdSegmentExpiredException(IdSegment current, IdSegment next) {
        super(Strings.lenientFormat("The next IdSegment:[%s] cannot be before the current IdSegment:[%s]-- times:[%s].",
                next,
                current,
                times.incrementAndGet())
        );
        this.current = current;
        this.next = next;
    }

    public IdSegment getCurrent() {
        return current;
    }

    public IdSegment getNext() {
        return next;
    }
}
