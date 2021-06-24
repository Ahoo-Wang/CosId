package me.ahoo.cosid;

import me.ahoo.cosid.snowflake.SnowflakeIdStateParser;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * @author ahoo wang
 */
public class DefaultIdGeneratorProvider implements IdGeneratorProvider {
    private final IdGeneratorHolder shareIdGeneratorHolder;

    private final ConcurrentHashMap<String, IdGeneratorHolder> nameMapIdGen;

    public DefaultIdGeneratorProvider(IdGenerator shareIdGen) {
        this.shareIdGeneratorHolder = new IdGeneratorHolder(shareIdGen);
        this.nameMapIdGen = new ConcurrentHashMap<>();
    }

    @Override
    public IdGenerator getShare() {
        return shareIdGeneratorHolder.getIdGenerator();
    }

    @Override
    public SnowflakeIdStateParser getShareSnowflakeIdStateParser() {
        return shareIdGeneratorHolder.getSnowflakeIdStateParser();
    }

    @Override
    public Optional<IdGenerator> get(String name) {
        Optional<IdGeneratorHolder> idGeneratorHolder = getHolder(name);
        return idGeneratorHolder.map(IdGeneratorHolder::getIdGenerator);
    }

    @Override
    public Optional<IdGeneratorHolder> getHolder(String name) {
        IdGeneratorHolder idGeneratorHolder = nameMapIdGen.get(name);
        return Optional.ofNullable(idGeneratorHolder);
    }

    @Override
    public Optional<SnowflakeIdStateParser> getSnowflakeIdStateParser(String name) {
        Optional<IdGeneratorHolder> idGeneratorHolder = getHolder(name);
        return idGeneratorHolder.map(IdGeneratorHolder::getSnowflakeIdStateParser);
    }

    @Override
    public void set(String name, IdGenerator idGenerator) {
        nameMapIdGen.put(name, new IdGeneratorHolder(idGenerator));
    }

    @Override
    public IdGenerator getOrCreate(String name, Supplier<IdGenerator> idGenSupplier) {
        return nameMapIdGen.computeIfAbsent(name, (__) -> {
            IdGenerator idGenerator = idGenSupplier.get();
            return new IdGeneratorHolder(idGenerator);
        }).getIdGenerator();
    }


}
