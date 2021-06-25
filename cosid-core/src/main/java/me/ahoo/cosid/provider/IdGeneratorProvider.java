package me.ahoo.cosid.provider;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeIdStateParser;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * @author ahoo wang
 */
public interface IdGeneratorProvider {

    IdGenerator getShare();

    void setShare(IdGenerator idGenerator);

    SnowflakeIdStateParser getShareSnowflakeIdStateParser();

    Optional<IdGenerator> get(String name);

    Optional<IdGeneratorHolder> getHolder(String name);

    Optional<SnowflakeIdStateParser> getSnowflakeIdStateParser(String name);

    void set(String name, IdGenerator idGenerator);

    IdGenerator getOrCreate(String name, Supplier<IdGenerator> idGenSupplier);

    class IdGeneratorHolder {
        private final IdGenerator idGenerator;
        private final SnowflakeIdStateParser snowflakeIdStateParser;

        public IdGeneratorHolder(IdGenerator idGenerator) {
            this.idGenerator = idGenerator;
            if (idGenerator instanceof SnowflakeId) {
                snowflakeIdStateParser = SnowflakeIdStateParser.of((SnowflakeId) idGenerator);
            } else {
                this.snowflakeIdStateParser = null;
            }
        }

        public IdGenerator getIdGenerator() {
            return idGenerator;
        }

        public SnowflakeIdStateParser getSnowflakeIdStateParser() {
            return snowflakeIdStateParser;
        }
    }
}
