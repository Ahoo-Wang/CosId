package me.ahoo.cosid;

import com.google.errorprone.annotations.ThreadSafe;
import org.jspecify.annotations.NonNull;

/**
 * Decorator pattern interface for wrapping and enhancing ID generators.
 * 
 * <p>This interface implements the Decorator design pattern, allowing implementations
 * to wrap existing ID generators and add additional functionality without modifying
 * the original generator. This is commonly used for adding features like:
 * <ul>
 *   <li>Custom ID conversion</li>
 *   <li>Additional validation</li>
 *   <li>Logging or monitoring</li>
 *   <li>Caching or buffering</li>
 * </ul>
 * 
 * <p>The decorator pattern enables flexible composition of ID generator features,
 * allowing multiple decorators to be chained together to create complex behavior.
 * 
 * <p>Implementations of this interface are expected to be thread-safe and can be
 * used concurrently across multiple threads.
 *
 * @param <D> The type of the decorated object
 * @author ahoo wang
 */
@ThreadSafe
public interface Decorator<D> {
    /**
     * Get the actual (wrapped) object that this decorator is enhancing.
     * 
     * <p>This method returns the underlying object that this decorator is wrapping.
     * For ID generators, this would typically be the base generator that is being
     * enhanced with additional functionality.
     *
     * @return The actual object being decorated
     */
    @NonNull
    D getActual();
    
    /**
     * Recursively get the actual object from a potentially nested decorator chain.
     * 
     * <p>This utility method traverses a chain of decorators to find the original
     * undecorated object. If the provided object is a decorator, it will recursively
     * call this method on the decorator's actual object until it finds a non-decorator
     * object.
     *
     * @param <D> The type of the object
     * @param any The object to unwrap (may be a decorator or actual object)
     * @return The unwrapped actual object
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <D> D getActual(D any) {
        if (any instanceof Decorator decorator) {
            return getActual((D) decorator.getActual());
        }
        return any;
    }

}
