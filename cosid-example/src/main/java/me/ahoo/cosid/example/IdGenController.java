package me.ahoo.cosid.example;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * @author ahoo wang
 */
@RestController("id")
public class IdGenController {
    private final IdGenerator shareIdGenerator;
    private final IdGeneratorProvider idGeneratorProvider;

    public IdGenController(IdGenerator shareIdGenerator, IdGeneratorProvider idGeneratorProvider) {
        this.shareIdGenerator = shareIdGenerator;
        this.idGeneratorProvider = idGeneratorProvider;
    }

    @GetMapping("{name}")
    public long generate(@PathVariable String name) {
        IdGenerator idGenerator = getIdGenerator(name);
        return idGenerator.generate();
    }

    private IdGenerator getIdGenerator(String name) {
        Preconditions.checkNotNull(name, "name can not be null");
        Optional<IdGenerator> optionalIdGenerator = idGeneratorProvider.get(name);
        if (!optionalIdGenerator.isPresent()) {
            throw new IllegalArgumentException(Strings.lenientFormat("idGenerator:[%s] not fond.", name));
        }
        return optionalIdGenerator.get();
    }

    @GetMapping("{name}/friendlyId")
    public String friendlyId(@PathVariable String name) {
        IdGenerator idGenerator = getIdGenerator(name);
        if (idGenerator instanceof SnowflakeFriendlyId) {
            return ((SnowflakeFriendlyId) idGenerator).friendlyId().getFriendlyId();
        }

        throw new IllegalArgumentException(Strings.lenientFormat("idGenerator:[%s] is not SnowflakeFriendlyId.", name));
    }
}
