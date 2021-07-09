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


import io.lettuce.core.RedisClient;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.SegmentChainId;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ahoo wang
 */
public class RedisIdFactory implements AutoCloseable {

    public static final RedisIdFactory INSTANCE = new RedisIdFactory();

    AtomicInteger counter = new AtomicInteger();
    RedisClient redisClient;
    private RedisIdFactory() {

    }
    public synchronized RedisIdSegmentDistributor createDistributor(int step) {
        if (redisClient == null) {
            redisClient = RedisClient.create("redis://localhost:6379");
        }
        String namespace = "rbh-" + counter.incrementAndGet();
        return new RedisIdSegmentDistributor(
                namespace,
                String.valueOf(step),
                0,
                step,
                RedisIdSegmentDistributor.DEFAULT_TIMEOUT,
                redisClient.connect().async());
    }


    public DefaultSegmentId createSegmentId(int step) {
        RedisIdSegmentDistributor distributor = createDistributor(step);
        return new DefaultSegmentId(distributor);
    }


    public SegmentChainId createSegmentChainId(int step) {
        RedisIdSegmentDistributor distributor = createDistributor(step);
        return new SegmentChainId(distributor);
    }

    @Override
    public void close() {
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }
}
