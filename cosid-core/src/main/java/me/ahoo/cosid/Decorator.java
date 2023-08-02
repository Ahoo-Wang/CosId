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
    
    @SuppressWarnings({"rawtypes"})
    static String chain(Object any) {
        StringBuilder builder = new StringBuilder();
        builder.append(any.getClass().getSimpleName());
        
        while (any instanceof Decorator decorator) {
            any = decorator.getActual();
            builder.append(" -> ").append(any.getClass().getSimpleName());
        }
        
        return builder.toString();
    }
}
