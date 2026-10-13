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

package me.ahoo.cosid.spring.redis;

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;

import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;


/**
 * Spring Redis IdSegmentDistributor Factory.
 *
 * @author ahoo wang
 */
public class SpringRedisIdSegmentDistributorFactory implements IdSegmentDistributorFactory {
    
    private final StringRedisTemplate redisTemplate;
    private final RedisCommandTimeout commandTimeout;
    private final RedisReplicaWait replicaWait;
    
    public SpringRedisIdSegmentDistributorFactory(StringRedisTemplate redisTemplate) {
        this(redisTemplate, RedisCommandTimeout.NONE);
    }
    
    public SpringRedisIdSegmentDistributorFactory(StringRedisTemplate redisTemplate, RedisCommandTimeout commandTimeout) {
        this(redisTemplate, commandTimeout, RedisReplicaWait.NONE);
    }
    
    /**
     * Create the factory.
     *
     * @param replicaWait the replicas each increment waits for
     * @since 4.1.0
     */
    public SpringRedisIdSegmentDistributorFactory(StringRedisTemplate redisTemplate, RedisCommandTimeout commandTimeout, RedisReplicaWait replicaWait) {
        this.redisTemplate = redisTemplate;
        this.commandTimeout = commandTimeout;
        this.replicaWait = replicaWait;
    }
    
    /**
     * The replicas each increment waits for.
     *
     * @since 4.1.0
     */
    public RedisReplicaWait getReplicaWait() {
        return replicaWait;
    }
    
    public RedisCommandTimeout getCommandTimeout() {
        return commandTimeout;
    }
    
    @Override
    public @NonNull IdSegmentDistributor create(IdSegmentDistributorDefinition definition) {
        SpringRedisIdSegmentDistributor springRedisIdSegmentDistributor = new SpringRedisIdSegmentDistributor(
            definition.getNamespace(),
            definition.getName(),
            definition.getOffset(),
            definition.getStep(),
            redisTemplate,
            commandTimeout,
            replicaWait);
        springRedisIdSegmentDistributor.ensureOffset();
        return springRedisIdSegmentDistributor;
    }
}
