package me.ahoo.cosid.spring.boot.starter.segment;

import com.google.common.base.MoreObjects;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.redis.RedisIdSegmentDistributor;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.SegmentChainId;
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
        SegmentId shareIdGen = createSegmentId(IdGeneratorProvider.SHARE, shareIdDefinition, redisConnectionFactory);
        if (Objects.isNull(idGeneratorProvider.getShare())) {
            idGeneratorProvider.setShare(shareIdGen);
        }
        if (Objects.isNull(segmentIdProperties.getProvider())) {
            return shareIdGen;
        }
        segmentIdProperties.getProvider().forEach((name, idDefinition) -> {
            SegmentId idGenerator = createSegmentId(name, idDefinition, redisConnectionFactory);
            idGeneratorProvider.set(name, idGenerator);
        });

        return shareIdGen;
    }

    @Bean
    @ConditionalOnMissingBean
    public LifecycleSegmentChainId lifecycleSegmentChainId(IdGeneratorProvider idGeneratorProvider) {
        return new LifecycleSegmentChainId(idGeneratorProvider);
    }

    private SegmentId createSegmentId(String name, SegmentIdProperties.IdDefinition idDefinition, RedisConnectionFactory redisConnectionFactory) {
        RedisIdSegmentDistributor redisIdSegmentDistributor = new RedisIdSegmentDistributor(
                cosIdProperties.getNamespace(),
                name,
                idDefinition.getOffset(),
                idDefinition.getStep(),
                segmentIdProperties.getTimeout(),
                redisConnectionFactory.getShareAsyncCommands());

        SegmentIdProperties.Mode mode = MoreObjects.firstNonNull(idDefinition.getMode(), segmentIdProperties.getMode());
        if (SegmentIdProperties.Mode.DEFAULT.equals(mode)) {
            return new DefaultSegmentId(redisIdSegmentDistributor);
        }
        SegmentIdProperties.Chain clain = MoreObjects.firstNonNull(idDefinition.getChain(), segmentIdProperties.getChain());
        return new SegmentChainId(clain.getSafeDistance(), clain.getPrefetchPeriod(), redisIdSegmentDistributor);
    }

}
