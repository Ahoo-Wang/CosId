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

package me.ahoo.cosid.redis;

import static me.ahoo.cosid.redis.RedisMachineIdDistributor.hashTag;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.segment.IdSegmentDistributor;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Redis IdSegmentDistributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class RedisIdSegmentDistributor implements IdSegmentDistributor {
    
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(1);
    
    private final String namespace;
    private final String name;
    /**
     * hash tag : namespace.name
     * cosid:{namespace.name}:adder
     */
    private final String adderKey;
    private final long offset;
    private final long step;
    private final Duration timeout;
    private final RedisClusterReactiveCommands<String, String> redisCommands;
    private volatile long lastMaxId;
    
    public RedisIdSegmentDistributor(String namespace,
                                     String name,
                                     RedisClusterReactiveCommands<String, String> redisCommands) {
        this(namespace, name, DEFAULT_OFFSET, DEFAULT_STEP, DEFAULT_TIMEOUT, redisCommands);
    }
    
    public RedisIdSegmentDistributor(String namespace,
                                     String name,
                                     long offset,
                                     long step,
                                     Duration timeout,
                                     RedisClusterReactiveCommands<String, String> redisCommands) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name can not be empty!");
        Preconditions.checkArgument(offset >= 0, "offset:[%s] must be greater than or equal to 0!", offset);
        Preconditions.checkArgument(step > 0, "step:[%s] must be greater than 0!", step);
        
        this.namespace = namespace;
        this.name = name;
        this.offset = offset;
        this.step = step;
        this.timeout = timeout;
        this.redisCommands = redisCommands;
        this.adderKey = CosId.COSID + ":" + hashTag(getNamespacedName()) + ".adder";
    }
    
    void ensureOffset() {
        if (log.isDebugEnabled()) {
            log.debug("Ensure Offset [{}] offset:[{}].", adderKey, offset);
        }
        Boolean notExists = redisCommands.setnx(this.adderKey, String.valueOf(offset)).block(timeout);
        if (log.isDebugEnabled()) {
            log.debug("Ensure Offset [{}] offset:[{}] - notExists:[{}].", adderKey, offset, notExists);
        }
    }
    
    public String getAdderKey() {
        return adderKey;
    }
    
    @Override
    public String getNamespace() {
        return namespace;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    public long getOffset() {
        return offset;
    }
    
    @Override
    public long getStep() {
        return step;
    }
    
    @Override
    public long nextMaxId(long step) {
        IdSegmentDistributor.ensureStep(step);
        if (log.isDebugEnabled()) {
            log.debug("Next MaxId [{}] step:[{}].", adderKey, step);
        }
        final long nextMinMaxId = lastMaxId + step;
        
        Long nextMaxId = redisCommands.incrby(adderKey, step).block(timeout);
        assert nextMaxId != null;
        Preconditions.checkNotNull(nextMaxId, "nextMaxId can not be null!");
        if (log.isDebugEnabled()) {
            log.debug("Next MaxId [{}] step:[{}] - nextMaxId:[{}].", adderKey, step, nextMaxId);
        }
        
        Preconditions.checkState(nextMaxId >= nextMinMaxId, "nextMaxId:[%s] must be greater than nextMinMaxId:[%s]!", nextMaxId, nextMinMaxId);
        this.lastMaxId = nextMaxId;
        return nextMaxId;
    }
}
