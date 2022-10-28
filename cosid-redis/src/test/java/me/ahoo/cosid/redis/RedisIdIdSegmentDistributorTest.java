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

import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.segment.distributor.IdSegmentDistributorSpec;
import me.ahoo.cosky.core.redis.RedisConfig;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;

import io.lettuce.core.resource.ClientResources;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.util.Objects;

/**
 * @author ahoo wang
 */
public class RedisIdIdSegmentDistributorTest extends IdSegmentDistributorSpec {
    protected RedisIdSegmentDistributor redisMaxIdDistributor;
    protected RedisConnectionFactory redisConnectionFactory;
    protected ClientResources clientResources;
    @BeforeEach
    void setup() {
        RedisConfig redisConfig = new RedisConfig();
        redisConfig.setUrl("redis://localhost:6379");
        clientResources= ClientResources.builder().build();
        redisConnectionFactory = new RedisConnectionFactory(clientResources, redisConfig);
        redisMaxIdDistributor =
            new RedisIdSegmentDistributor(MockIdGenerator.INSTANCE.generateAsString(), "RedisIdGeneratorTest", 0, 100, RedisIdSegmentDistributor.DEFAULT_TIMEOUT,
                redisConnectionFactory.getShareReactiveCommands());
    }
    
    @SneakyThrows
    @AfterEach
    private void destroy() {
        if (Objects.nonNull(redisConnectionFactory)) {
            redisConnectionFactory.close();
        }
        if (Objects.nonNull(clientResources)) {
            clientResources.shutdown();
        }
    }
    
    @Override
    protected IdSegmentDistributorFactory getFactory() {
        return new RedisIdSegmentDistributorFactory(redisConnectionFactory, Duration.ofSeconds(2));
    }
    
    @Override
    protected void setMaxIdBack(IdSegmentDistributor distributor, long maxId) {
        String adderKey = ((RedisIdSegmentDistributor) distributor).getAdderKey();
        redisConnectionFactory.getShareSyncCommands().set(adderKey, String.valueOf(maxId - 1));
    }
    
}
