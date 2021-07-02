package me.ahoo.cosid.spring.boot.starter.segment;

import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.redis.RedisIdSegmentDistributor;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSegmentEnabled
@EnableConfigurationProperties(SegmentIdProperties.class)
public class CosIdSegmentAutoConfiguration {

    private final CosIdProperties cosIdProperties;
    private final SegmentIdProperties segmentIdProperties;

    public CosIdSegmentAutoConfiguration(CosIdProperties cosIdProperties, SegmentIdProperties segmentIdProperties) {
        this.cosIdProperties = cosIdProperties;
        this.segmentIdProperties = segmentIdProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(value = SegmentIdProperties.Distributor.TYPE, matchIfMissing = true, havingValue = "redis")
    public SegmentId shareSegmentId(RedisConnectionFactory redisConnectionFactory, IdGeneratorProvider idGeneratorProvider) {
        SegmentIdProperties.IdDefinition shareIdDefinition = segmentIdProperties.getShare();
        RedisIdSegmentDistributor shareRedisMaxIdDistributor = new RedisIdSegmentDistributor(cosIdProperties.getNamespace(), IdGeneratorProvider.SHARE, shareIdDefinition.getOffset(), shareIdDefinition.getStep(), segmentIdProperties.getTimeout(), redisConnectionFactory.getShareAsyncCommands());
        SegmentId shareIdGen = new DefaultSegmentId(shareRedisMaxIdDistributor);
        if (Objects.isNull(idGeneratorProvider.getShare())) {
            idGeneratorProvider.setShare(shareIdGen);
        }
        if (Objects.isNull(segmentIdProperties.getProvider())) {
            return shareIdGen;
        }
        segmentIdProperties.getProvider().forEach((name, idDefinition) -> {
            RedisIdSegmentDistributor redisMaxIdDistributor = new RedisIdSegmentDistributor(cosIdProperties.getNamespace(), name, idDefinition.getOffset(), idDefinition.getStep(), segmentIdProperties.getTimeout(), redisConnectionFactory.getShareAsyncCommands());
            SegmentId idGenerator = new DefaultSegmentId(redisMaxIdDistributor);
            idGeneratorProvider.set(name, idGenerator);
        });

        return shareIdGen;
    }

}
