package me.ahoo.cosid.example;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.IdGeneratorProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ahoo wang
 */
@RestController
public class IdGenController {
    private final IdGenerator shareIdGenerator;
    private final IdGeneratorProvider idGeneratorProvider;

    public IdGenController(IdGenerator shareIdGenerator, IdGeneratorProvider idGeneratorProvider) {
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
