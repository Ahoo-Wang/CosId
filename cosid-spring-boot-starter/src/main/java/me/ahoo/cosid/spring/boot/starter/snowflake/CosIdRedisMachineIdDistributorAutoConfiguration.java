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

package me.ahoo.cosid.spring.boot.starter.snowflake;

import static me.ahoo.cosid.snowflake.machine.AbstractMachineIdDistributor.FOREVER_SAFE_GUARD_DURATION;

import me.ahoo.cosid.redis.RedisMachineIdDistributor;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;

import com.google.common.base.Preconditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * CosId Redis MachineIdDistributor AutoConfiguration.
 *
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSnowflakeEnabled
@ConditionalOnClass(RedisMachineIdDistributor.class)
@ConditionalOnProperty(value = SnowflakeIdProperties.Machine.Distributor.TYPE, havingValue = "redis")
public class CosIdRedisMachineIdDistributorAutoConfiguration {
    
    private final SnowflakeIdProperties snowflakeIdProperties;
    
    public CosIdRedisMachineIdDistributorAutoConfiguration(SnowflakeIdProperties snowflakeIdProperties) {
        this.snowflakeIdProperties = snowflakeIdProperties;
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisMachineIdDistributor redisMachineIdDistributor(RedisConnectionFactory redisConnectionFactory, MachineStateStorage localMachineState,
                                                               ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        Preconditions.checkNotNull(redisConnectionFactory, "redisConnectionFactory can not be null.");
        Duration timeout = snowflakeIdProperties.getMachine().getDistributor().getRedis().getTimeout();
        return new RedisMachineIdDistributor(timeout, redisConnectionFactory.getShareReactiveCommands(), localMachineState, clockBackwardsSynchronizer, FOREVER_SAFE_GUARD_DURATION);
    }
    
    
}
