package me.ahoo.cosid;

import me.ahoo.cosid.stat.generator.IdGeneratorStat;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;

/**
 * IdGenerator decorator.
 *
 * @author ahoo wang
 */
@ThreadSafe
public interface IdGeneratorDecorator extends IdGenerator, Decorator<IdGenerator> {
    /**
     * Get decorator actual id generator.
     *
     * @return actual id generator
     */
    @Nonnull
    IdGenerator getActual();

    static <T extends IdGenerator> T getActual(T idGenerator) {
        return Decorator.getActual(idGenerator);
    }

    @Override
    default long generate() {
        return getActual().generate();
    }

    @Override
    default IdGeneratorStat stat() {
        return IdGeneratorStat.simple(getClass().getSimpleName(), getActual().stat(), idConverter().stat());
    }
}
