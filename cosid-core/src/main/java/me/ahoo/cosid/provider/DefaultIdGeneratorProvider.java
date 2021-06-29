package me.ahoo.cosid.provider;

import me.ahoo.cosid.IdGenerator;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * @author ahoo wang
 */
public class DefaultIdGeneratorProvider implements IdGeneratorProvider {
    private IdGenerator shareIdGenerator;

    private final ConcurrentHashMap<String, IdGenerator> nameMapIdGen;

    public DefaultIdGeneratorProvider() {
        this.nameMapIdGen = new ConcurrentHashMap<>();
    }

    @Override
    public IdGenerator getShare() {
        return shareIdGenerator;
    }

    @Override
    public void setShare(IdGenerator idGenerator) {
        this.shareIdGenerator = idGenerator;
    }

    @Override
    public Optional<IdGenerator> get(String name) {
        IdGenerator idGen = nameMapIdGen.get(name);
        return Optional.ofNullable(idGen);
    }

    @Override
    public void set(String name, IdGenerator idGenerator) {
        nameMapIdGen.put(name, idGenerator);
    }

    @Override
    public IdGenerator getOrCreate(String name, Supplier<IdGenerator> idGenSupplier) {
        return nameMapIdGen.computeIfAbsent(name, (__) -> idGenSupplier.get());
    }

}
