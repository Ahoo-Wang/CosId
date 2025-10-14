package me.ahoo.cosid;

import me.ahoo.cosid.stat.generator.IdGeneratorStat;

import com.google.errorprone.annotations.ThreadSafe;
import jakarta.annotation.Nonnull;

/**
 * IdGenerator decorator for enhancing ID generator functionality.
 * 
 * <p>This interface combines the {@link IdGenerator} and {@link Decorator} interfaces
 * to create a specialized decorator for ID generators. It allows implementations to
 * wrap existing ID generators and add additional functionality while maintaining
 * the full ID generation API.
 * 
 * <p>Common use cases for ID generator decorators include:
 * <ul>
 *   <li>Custom ID conversion (using different {@link IdConverter} implementations)</li>
 *   <li>Adding prefixes or suffixes to generated IDs</li>
 *   <li>Implementing caching or buffering of generated IDs</li>
 *   <li>Adding monitoring or logging capabilities</li>
 *   <li>Clock synchronization for time-based ID generators</li>
 * </ul>
 * 
 * <p>Implementations of this interface are expected to be thread-safe and can be
 * used concurrently across multiple threads.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface IdGeneratorDecorator extends IdGenerator, Decorator<IdGenerator> {
    /**
     * Get the actual (wrapped) ID generator that this decorator is enhancing.
     * 
     * <p>This method returns the underlying ID generator that this decorator is wrapping.
     * All ID generation requests are typically delegated to this actual generator, with
     * the decorator adding its additional functionality.
     *
     * @return The actual ID generator being decorated
     */
    @Nonnull
    IdGenerator getActual();

    /**
     * Recursively get the actual ID generator from a potentially nested decorator chain.
     * 
     * <p>This utility method traverses a chain of ID generator decorators to find the
     * original undecorated ID generator. If the provided ID generator is a decorator,
     * it will recursively call this method on the decorator's actual generator until
     * it finds a non-decorator generator.
     *
     * @param <T> The type of the ID generator
     * @param idGenerator The ID generator to unwrap (may be a decorator or actual generator)
     * @return The unwrapped actual ID generator
     */
    static <T extends IdGenerator> T getActual(T idGenerator) {
        return Decorator.getActual(idGenerator);
    }

    /**
     * Generate a distributed ID by delegating to the actual generator.
     * 
     * <p>This default implementation delegates the ID generation to the actual
     * wrapped generator, ensuring that decorators can focus on adding functionality
     * rather than reimplementing basic generation.
     *
     * @return A unique distributed ID as a long value
     */
    @Override
    default long generate() {
        return getActual().generate();
    }

    /**
     * Get statistical information about this decorated ID generator.
     * 
     * <p>This method provides combined statistical information from both the decorator
     * itself and the actual generator it wraps, as well as the ID converter being used.
     * This gives a complete picture of the generator's state.
     *
     * @return Statistical information about this decorated ID generator
     */
    @Override
    default IdGeneratorStat stat() {
        return IdGeneratorStat.simple(getClass().getSimpleName(), getActual().stat(), idConverter().stat());
    }
}
