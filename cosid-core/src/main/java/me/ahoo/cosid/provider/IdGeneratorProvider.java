package me.ahoo.cosid.provider;

import me.ahoo.cosid.IdGenerator;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * @author ahoo wang
 */
public interface IdGeneratorProvider {
    String SHARE = "__share__";

    IdGenerator getShare();

    void setShare(IdGenerator idGenerator);

    Optional<IdGenerator> get(String name);

    void set(String name, IdGenerator idGenerator);

    IdGenerator getOrCreate(String name, Supplier<IdGenerator> idGenSupplier);

}
