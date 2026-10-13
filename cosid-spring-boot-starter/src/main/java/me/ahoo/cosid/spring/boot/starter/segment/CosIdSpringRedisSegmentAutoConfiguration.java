/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.store.BuiltInStoreProvider;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnBuiltInSegmentStore;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnSegmentStoreType;
import me.ahoo.cosid.spring.redis.RedisCommandTimeout;
import me.ahoo.cosid.spring.redis.RedisReplicaWait;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributor;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributorFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * CosId Spring Redis Segment AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSegmentEnabled
@EnableConfigurationProperties(SegmentIdProperties.class)
@ConditionalOnClass(SpringRedisIdSegmentDistributor.class)
@ConditionalOnSegmentStoreType("redis")
public class CosIdSpringRedisSegmentAutoConfiguration {

    private final SegmentIdProperties segmentIdProperties;

    public CosIdSpringRedisSegmentAutoConfiguration(SegmentIdProperties segmentIdProperties) {
        this.segmentIdProperties = segmentIdProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBuiltInSegmentStore
    public IdSegmentDistributorFactory idSegmentDistributorFactory(StringRedisTemplate stringRedisTemplate) {
        SegmentIdProperties.Distributor.Redis redis = segmentIdProperties.getDistributor().getRedis();
        RedisCommandTimeout commandTimeout = RedisCommandTimeout.of(redis.getTimeout());
        return new SpringRedisIdSegmentDistributorFactory(stringRedisTemplate, commandTimeout,
            replicaWait(redis.getWaitReplicas(), redis.getWaitTimeout(), commandTimeout, SegmentIdProperties.PREFIX + ".distributor.redis"));
    }

    /**
     * The Redis backend for {@link me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider} selection.
     */
    @Bean
    public BuiltInStoreProvider cosIdRedisSegmentStoreProvider(ObjectProvider<StringRedisTemplate> stringRedisTemplate) {
        return BuiltInStoreProvider.segment("redis", context -> idSegmentDistributorFactory(stringRedisTemplate.getObject()));
    }
    
    /**
     * The replica wait of {@code prefix.wait-replicas} and {@code prefix.wait-timeout}, which must end before the command
     * timeout that bounds it.
     */
    static RedisReplicaWait replicaWait(int replicas, Duration waitTimeout, RedisCommandTimeout commandTimeout, String prefix) {
        RedisReplicaWait replicaWait = RedisReplicaWait.of(replicas, waitTimeout);
        if (replicaWait.isEnabled() && commandTimeout.isEnabled() && replicaWait.getTimeout().compareTo(commandTimeout.getTimeout()) >= 0) {
            throw new IllegalStateException(prefix + ".wait-timeout [" + waitTimeout + "] must be shorter than " + prefix + ".timeout ["
                + commandTimeout.getTimeout() + "], which bounds the write and its wait.");
        }
        return replicaWait;
    }
}
