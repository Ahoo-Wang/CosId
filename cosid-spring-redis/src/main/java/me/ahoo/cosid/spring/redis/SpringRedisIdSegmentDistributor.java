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

package me.ahoo.cosid.spring.redis;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.CosId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;

import static me.ahoo.cosid.spring.redis.SpringRedisMachineIdDistributor.hashTag;

/**
 * @author ahoo wang
 */
@Slf4j
public class SpringRedisIdSegmentDistributor implements IdSegmentDistributor {

    public static final Resource REDIS_ID_GENERATE_SOURCE = new ClassPathResource("redis_id_generate.lua");
    public static final RedisScript<Long> REDIS_ID_GENERATE = RedisScript.of(REDIS_ID_GENERATE_SOURCE, Long.class);

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
        List<String> keys = Collections.singletonList(adderKey);
        Object[] values = {String.valueOf(offset), String.valueOf(step)};
        Long nextMaxId = redisTemplate.execute(REDIS_ID_GENERATE, keys, values);
        Preconditions.checkNotNull(nextMaxId,"nextMaxId can not be null!");
        if (log.isDebugEnabled()) {
            log.debug("nextMaxId - step:[{}] - nextMaxId:[{}].", step, nextMaxId);
        }
        return nextMaxId;
    }


}
