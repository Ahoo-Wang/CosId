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

import static me.ahoo.cosid.spring.redis.SpringRedisMachineIdDistributor.hashTag;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.segment.IdSegmentDistributor;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * Spring Redis IdSegmentDistributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class SpringRedisIdSegmentDistributor implements IdSegmentDistributor {
    
    /**
     * Initialize the adder to the offset when the key is missing and increment it, atomically.
     * A plain {@code INCRBY} would restart from {@code 0} after the key is evicted, flushed or lost in a failover,
     * and hand out ranges that were already used.
     */
    public static final RedisScript<Long> REDIS_ID_GENERATE = RedisScript.of(new ClassPathResource("redis_id_generate.lua"), Long.class);
    private static final AtomicLongFieldUpdater<SpringRedisIdSegmentDistributor> LAST_MAX_ID =
        AtomicLongFieldUpdater.newUpdater(SpringRedisIdSegmentDistributor.class, "lastMaxId");
    
    private final String namespace;
    private final String name;
    /**
     * hash tag : namespace.name
     * cosid:{namespace.name}:adder
     */
    private final String adderKey;
    private final long offset;
    private final long step;
    private final StringRedisTemplate redisTemplate;
    private volatile long lastMaxId;
    
    public SpringRedisIdSegmentDistributor(String namespace,
                                           String name,
                                           StringRedisTemplate redisTemplate) {
        this(namespace, name, DEFAULT_OFFSET, DEFAULT_STEP, redisTemplate);
    }
    
    public SpringRedisIdSegmentDistributor(String namespace,
                                           String name,
                                           long offset,
                                           long step,
                                           StringRedisTemplate redisTemplate) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(namespace), "namespace can not be empty!");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name can not be empty!");
        Preconditions.checkArgument(offset >= 0, "offset:[%s] must be greater than or equal to 0!", offset);
        Preconditions.checkArgument(step > 0, "step:[%s] must be greater than 0!", step);
        
        this.namespace = namespace;
        this.name = name;
        this.offset = offset;
        this.step = step;
        this.redisTemplate = redisTemplate;
        this.adderKey = CosId.COSID + ":" + hashTag(getNamespacedName()) + ".adder";
    }
    
    void ensureOffset() {
        if (log.isDebugEnabled()) {
            log.debug("Ensure Offset [{}] offset:[{}].", adderKey, offset);
        }
        Boolean notExists = redisTemplate.opsForValue().setIfAbsent(adderKey, String.valueOf(offset));
        if (log.isDebugEnabled()) {
            log.debug("Ensure Offset [{}] offset:[{}] - notExists:[{}].", adderKey, offset, notExists);
        }
    }
    
    public String getAdderKey() {
        return adderKey;
    }
    
    @Override
    public @NonNull String getNamespace() {
        return namespace;
    }
    
    @Override
    public @NonNull String getName() {
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
        Long nextMaxId = redisTemplate.execute(REDIS_ID_GENERATE, Collections.singletonList(adderKey), String.valueOf(offset), String.valueOf(step));
        Preconditions.checkNotNull(nextMaxId, "nextMaxId can not be null!");
        if (log.isDebugEnabled()) {
            log.debug("Next MaxId [{}] step:[{}] - nextMaxId:[{}].", adderKey, step, nextMaxId);
        }
        Preconditions.checkState(nextMaxId >= nextMinMaxId, "nextMaxId:[%s] must be greater than nextMinMaxId:[%s]!", nextMaxId, nextMinMaxId);
        LAST_MAX_ID.accumulateAndGet(this, nextMaxId, Math::max);
        return nextMaxId;
    }
    
    
}
