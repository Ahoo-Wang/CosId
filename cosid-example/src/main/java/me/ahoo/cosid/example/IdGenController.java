package me.ahoo.cosid.example;

import me.ahoo.cosid.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ahoo wang
 */
@RestController
public class IdGenController {
    private final MillisecondSnowflakeId shareIdGenerator;
    private final IdGeneratorProvider idGeneratorProvider;

    public IdGenController(MillisecondSnowflakeId shareIdGenerator, IdGeneratorProvider idGeneratorProvider) {
        this.shareIdGenerator = shareIdGenerator;
        this.idGeneratorProvider = idGeneratorProvider;
    }

    @GetMapping("gen")
    public long gen() {
        return shareIdGenerator.generate();
    }

    @GetMapping("genFriendlyId")
    public String genFriendlyId() {
        long id = shareIdGenerator.generate();
        return idGeneratorProvider.getShareSnowflakeIdStateParser().parse(id).getFriendlyId();
    }
}
