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

import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSegmentEnabled
@EnableConfigurationProperties(SegmentIdProperties.class)
@ConditionalOnClass(SpringRedisIdSegmentDistributor.class)
@ConditionalOnProperty(value = SegmentIdProperties.Distributor.TYPE, matchIfMissing = true, havingValue = "redis")
public class CosIdSpringRedisSegmentAutoConfiguration {

    private final CosIdProperties cosIdProperties;
    private final SegmentIdProperties segmentIdProperties;

    public CosIdSpringRedisSegmentAutoConfiguration(CosIdProperties cosIdProperties, SegmentIdProperties segmentIdProperties) {
        this.cosIdProperties = cosIdProperties;
        this.segmentIdProperties = segmentIdProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    public SegmentId shareSpringRedisSegmentId(StringRedisTemplate redisTemplate, IdGeneratorProvider idGeneratorProvider) {
        SegmentIdProperties.IdDefinition shareIdDefinition = segmentIdProperties.getShare();
        SegmentId shareIdGen = createSegmentIdOfSpring(IdGeneratorProvider.SHARE, shareIdDefinition, redisTemplate);
        if (Objects.isNull(idGeneratorProvider.getShare())) {
            idGeneratorProvider.setShare(shareIdGen);
        }
        if (Objects.isNull(segmentIdProperties.getProvider())) {
            return shareIdGen;
        }
        segmentIdProperties.getProvider().forEach((name, idDefinition) -> {
            SegmentId idGenerator = createSegmentIdOfSpring(name, idDefinition, redisTemplate);
            idGeneratorProvider.set(name, idGenerator);
        });

        return shareIdGen;
    }

    private SegmentId createSegmentIdOfSpring(String name, SegmentIdProperties.IdDefinition idDefinition, StringRedisTemplate redisTemplate) {
        SpringRedisIdSegmentDistributor redisIdSegmentDistributor = new SpringRedisIdSegmentDistributor(
                cosIdProperties.getNamespace(),
                name,
                idDefinition.getOffset(),
                idDefinition.getStep(),
                redisTemplate);
        return CosIdSegmentAutoConfiguration.createSegment(segmentIdProperties, idDefinition, redisIdSegmentDistributor);
    }

}
