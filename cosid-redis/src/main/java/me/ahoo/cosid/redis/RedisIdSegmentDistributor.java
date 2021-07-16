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

package me.ahoo.cosid.redis;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.CosId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosky.core.redis.RedisScripts;
import me.ahoo.cosky.core.util.Futures;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static me.ahoo.cosid.redis.RedisMachineIdDistributor.hashTag;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisIdSegmentDistributor implements IdSegmentDistributor {

    public static final String REDIS_ID_GENERATE = "redis_id_generate.lua";
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
    private final RedisClusterAsyncCommands<String, String> redisCommands;

    public RedisIdSegmentDistributor(String namespace,
                                     String name,
                                     RedisClusterAsyncCommands<String, String> redisCommands) {
        this(namespace, name, DEFAULT_OFFSET, DEFAULT_STEP, DEFAULT_TIMEOUT, redisCommands);
    }

    public RedisIdSegmentDistributor(String namespace,
                                     String name,
                                     long offset,
                                     long step,
                                     Duration timeout,
                                     RedisClusterAsyncCommands<String, String> redisCommands) {
        this.step = step;
        this.namespace = namespace;
        this.name = name;
        this.offset = offset;
        this.timeout = timeout;
        this.redisCommands = redisCommands;
        this.adderKey = CosId.COSID + ":" + hashTag(getNamespacedName()) + ".adder";
    }

    public String getNamespace() {
        return namespace;
    }

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
        long maxId = Futures.getUnChecked(fetchMaxIdAsync(step), timeout);
        if (log.isInfoEnabled()) {
            log.info("nextMaxId - step:[{}] - maxId:[{}].", step, maxId);
        }
        return maxId;
    }

    private CompletableFuture<Long> fetchMaxIdAsync(long step) {
        return RedisScripts.doEnsureScript(REDIS_ID_GENERATE, redisCommands,
                (scriptSha) -> {
                    String[] keys = {adderKey};
                    String[] values = {String.valueOf(offset), String.valueOf(step)};
                    return redisCommands.evalsha(scriptSha, ScriptOutputType.INTEGER, keys, values);
                }
        );
    }

}
