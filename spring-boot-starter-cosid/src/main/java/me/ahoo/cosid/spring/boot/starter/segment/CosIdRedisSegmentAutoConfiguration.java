/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
@ConditionalOnProperty(value = SegmentIdProperties.Distributor.TYPE, matchIfMissing = true, havingValue = "redis")
public class CosIdRedisSegmentAutoConfiguration {

    private final CosIdProperties cosIdProperties;
    private final SegmentIdProperties segmentIdProperties;

    public CosIdRedisSegmentAutoConfiguration(CosIdProperties cosIdProperties, SegmentIdProperties segmentIdProperties) {
        this.cosIdProperties = cosIdProperties;
        this.segmentIdProperties = segmentIdProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public SegmentId shareRedisSegmentId(RedisConnectionFactory redisConnectionFactory, IdGeneratorProvider idGeneratorProvider) {
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
        SegmentIdProperties.Chain chain = MoreObjects.firstNonNull(idDefinition.getChain(), segmentIdProperties.getChain());
        return new SegmentChainId(chain.getSafeDistance(), chain.getPrefetchPeriod(), redisIdSegmentDistributor);
    }

}
