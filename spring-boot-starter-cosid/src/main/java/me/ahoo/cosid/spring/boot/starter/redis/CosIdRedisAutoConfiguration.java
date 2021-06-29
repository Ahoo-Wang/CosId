package me.ahoo.cosid.spring.boot.starter.redis;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.redis.RedisIdGenerator;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdRedisEnabled
@EnableConfigurationProperties(RedisIdProperties.class)
public class CosIdRedisAutoConfiguration {

    private final CosIdProperties cosIdProperties;
    private final RedisIdProperties redisIdProperties;

    public CosIdRedisAutoConfiguration(CosIdProperties cosIdProperties, RedisIdProperties redisIdProperties) {
        this.cosIdProperties = cosIdProperties;
        this.redisIdProperties = redisIdProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisIdGenerator shareIdGenerator(RedisConnectionFactory redisConnectionFactory, IdGeneratorProvider idGeneratorProvider) {
        RedisIdProperties.IdDefinition shareIdDefinition = redisIdProperties.getShare();
        RedisIdGenerator shareIdGen = new RedisIdGenerator(cosIdProperties.getNamespace(), RedisIdProperties.SHARE, shareIdDefinition.getOffset(), shareIdDefinition.getStep(), redisIdProperties.getTimeout(), redisConnectionFactory.getShareAsyncCommands());
        idGeneratorProvider.setShare(shareIdGen);
        if (Objects.isNull(redisIdProperties.getProvider())) {
            return shareIdGen;
        }
        redisIdProperties.getProvider().forEach((name, idDefinition) -> {
            IdGenerator idGenerator = new RedisIdGenerator(cosIdProperties.getNamespace(), name, idDefinition.getOffset(), idDefinition.getStep(), redisIdProperties.getTimeout(), redisConnectionFactory.getShareAsyncCommands());
            idGeneratorProvider.set(name, idGenerator);
        });

        return shareIdGen;
    }

}
