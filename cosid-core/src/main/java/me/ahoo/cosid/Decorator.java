package me.ahoo.cosid;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Decorator.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface Decorator<D> {
    /**
     * Get decorator actual id generator.
     *
     * @return actual id generator
     */
    @Nonnull
    D getActual();
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <D> D getActual(D any) {
        if (any instanceof Decorator decorator) {
            return getActual((D) decorator.getActual());
        }
        return any;
    }

}
