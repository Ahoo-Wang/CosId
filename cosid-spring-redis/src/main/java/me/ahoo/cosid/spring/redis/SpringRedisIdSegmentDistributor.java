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
import me.ahoo.cosid.segment.AbstractIdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributor;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;

/**
 * Spring Redis IdSegmentDistributor.
 *
 * @author ahoo wang
 */
@Slf4j
public class SpringRedisIdSegmentDistributor extends AbstractIdSegmentDistributor {
    
    /**
     * Initialize the adder to the offset when the key is missing and increment it, atomically.
     * A plain {@code INCRBY} would restart from {@code 0} after the key is evicted, flushed or lost in a failover,
     * and hand out ranges that were already used.
     *
     * <p>The script returns the new max id as a string: Lua numbers are doubles and lose precision above 2^53.</p>
     *
     * <p>The offset only bounds a lost key from below. If the key is lost after the counter moved past the offset,
     * the counter restarts at the offset and segments already issued are handed out again; an instance started
     * after the loss has no earlier max id to compare with, so it cannot detect this. Use Redis persistence
     * (AOF) and replication if that matters.</p>
     */
    public static final RedisScript<String> REDIS_ID_GENERATE = RedisScript.of(new ClassPathResource("redis_id_generate.lua"), String.class);
    
    /**
     * hash tag : namespace.name
     * cosid:{namespace.name}:adder
     */
    private final String adderKey;
    private final long offset;
    private final StringRedisTemplate redisTemplate;
    private final RedisCommandTimeout commandTimeout;
    
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
        this(namespace, name, offset, step, redisTemplate, RedisCommandTimeout.NONE);
    }
    
    public SpringRedisIdSegmentDistributor(String namespace,
                                           String name,
                                           long offset,
                                           long step,
                                           StringRedisTemplate redisTemplate,
                                           RedisCommandTimeout commandTimeout) {
        super(namespace, name, step);
        Preconditions.checkArgument(offset >= 0, "offset:[%s] must be greater than or equal to 0!", offset);
        
        this.offset = offset;
        this.redisTemplate = redisTemplate;
        this.commandTimeout = Preconditions.checkNotNull(commandTimeout, "commandTimeout can not be null!");
        this.adderKey = CosId.COSID + ":" + hashTag(getNamespacedName()) + ".adder";
    }
    
    void ensureOffset() {
        if (log.isDebugEnabled()) {
            log.debug("Ensure Offset [{}] offset:[{}].", adderKey, offset);
        }
        // Not bounded by commandTimeout: this is the first command at startup and pays for connecting and class loading.
        Boolean notExists = redisTemplate.opsForValue().setIfAbsent(adderKey, String.valueOf(offset));
        if (log.isDebugEnabled()) {
            log.debug("Ensure Offset [{}] offset:[{}] - notExists:[{}].", adderKey, offset, notExists);
        }
    }
    
    public String getAdderKey() {
        return adderKey;
    }
    
    
    
    public long getOffset() {
        return offset;
    }
    
    
    @Override
    protected long doNextMaxId(long step) {
        if (log.isDebugEnabled()) {
            log.debug("Next MaxId [{}] step:[{}].", adderKey, step);
        }
        
        String nextMaxIdValue = commandTimeout.call("nextMaxId",
            () -> redisTemplate.execute(REDIS_ID_GENERATE, Collections.singletonList(adderKey), String.valueOf(offset), String.valueOf(step)));
        Preconditions.checkNotNull(nextMaxIdValue, "nextMaxId can not be null!");
        long nextMaxId = Long.parseLong(nextMaxIdValue);
        if (log.isDebugEnabled()) {
            log.debug("Next MaxId [{}] step:[{}] - nextMaxId:[{}].", adderKey, step, nextMaxId);
        }
        return nextMaxId;
    }
    
    
}
