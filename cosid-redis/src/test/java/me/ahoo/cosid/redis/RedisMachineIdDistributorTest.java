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

import static me.ahoo.cosid.redis.RedisMachineIdDistributor.DEFAULT_TIMEOUT;

import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.MachineIdDistributor;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;
import me.ahoo.cosid.test.snowflake.machine.distributor.DistributorSpec;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.util.Objects;

/**
 * @author ahoo wang
 */
class RedisMachineIdDistributorTest extends DistributorSpec {
    protected RedisClient redisClient;
    protected StatefulRedisConnection<String, String> redisConnection;
    protected RedisMachineIdDistributor redisMachineIdDistributor;
    
    @BeforeEach
    void setup() {
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        redisMachineIdDistributor = new RedisMachineIdDistributor(redisConnection.reactive(), MachineStateStorage.LOCAL, ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @AfterEach
    void destroy() {
        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }
    
    @Override
    protected MachineIdDistributor getDistributor() {
        return new RedisMachineIdDistributor(redisConnection.reactive(), MachineStateStorage.LOCAL, ClockBackwardsSynchronizer.DEFAULT);
    }
    
    @Override
    protected MachineIdDistributor getDistributor(Duration safeGuardDuration) {
        return new RedisMachineIdDistributor(DEFAULT_TIMEOUT, redisConnection.reactive(), MachineStateStorage.LOCAL, ClockBackwardsSynchronizer.DEFAULT, safeGuardDuration);
    }
}
