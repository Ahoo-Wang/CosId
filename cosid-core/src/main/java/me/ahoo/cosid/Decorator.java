package me.ahoo.cosid;

import com.google.errorprone.annotations.ThreadSafe;
import jakarta.annotation.Nonnull;

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
