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
    private final int offset;
    private final int step;
    private final StringRedisTemplate redisTemplate;

    public SpringRedisIdSegmentDistributor(String namespace,
                                           String name,
                                           StringRedisTemplate redisTemplate) {
        this(namespace, name, DEFAULT_OFFSET, DEFAULT_STEP,  redisTemplate);
    }

    public SpringRedisIdSegmentDistributor(String namespace,
                                           String name,
                                           int offset,
                                           int step,
                                           StringRedisTemplate redisTemplate) {
        this.step = step;
        this.namespace = namespace;
        this.name = name;
        this.offset = offset;
        this.redisTemplate = redisTemplate;
        this.adderKey = CosId.COSID + ":" + hashTag(getNamespacedName()) + ".adder";
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public int getOffset() {
        return offset;
    }

    @Override
    public int getStep() {
        return step;
    }

    @Override
    public long nextMaxId(int step) {
        List<String> keys = Collections.singletonList(adderKey);
        String[] values = {String.valueOf(offset), String.valueOf(step)};
        long maxId = redisTemplate.execute(REDIS_ID_GENERATE, keys, values);
        if (log.isDebugEnabled()) {
            log.debug("nextMaxId - step:[{}] - maxId:[{}].", step, maxId);
        }
        return maxId;
    }


}
