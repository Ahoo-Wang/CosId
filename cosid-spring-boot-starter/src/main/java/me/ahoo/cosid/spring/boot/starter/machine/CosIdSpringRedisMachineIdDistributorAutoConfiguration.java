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

package me.ahoo.cosid.spring.boot.starter.machine;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.machine.lease.MachineLeaseManager;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.store.BuiltInStoreProvider;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnBuiltInMachineStore;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnMachineStoreType;
import me.ahoo.cosid.spring.redis.RedisCommandTimeout;
import me.ahoo.cosid.spring.redis.RedisReplicaWait;
import me.ahoo.cosid.spring.redis.SpringRedisMachineLeaseStore;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * CosId Spring Redis MachineIdDistributor AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdMachineEnabled
@ConditionalOnMachineStoreType("redis")
@ConditionalOnClass(SpringRedisMachineLeaseStore.class)
@EnableConfigurationProperties(MachineProperties.class)
public class CosIdSpringRedisMachineIdDistributorAutoConfiguration {
    
    private final MachineProperties machineProperties;
    
    public CosIdSpringRedisMachineIdDistributorAutoConfiguration(MachineProperties machineProperties) {
        this.machineProperties = machineProperties;
    }
    
    /**
     * The machine id lease store on Redis: every command is bounded by {@code cosid.machine.distributor.redis.timeout}.
     * Claims and renewals wait for {@code wait-replicas} replicas, within {@code wait-timeout}.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBuiltInMachineStore
    public SpringRedisMachineLeaseStore springRedisMachineLeaseStore(StringRedisTemplate redisTemplate) {
        MachineProperties.Redis redis = machineProperties.getDistributor().getRedis();
        RedisCommandTimeout commandTimeout = RedisCommandTimeout.of(MachineLeaseStores.positive(redis.getTimeout(),
            MachineProperties.PREFIX + ".distributor.redis.timeout"));
        return new SpringRedisMachineLeaseStore(redisTemplate, commandTimeout,
            replicaWait(redis.getWaitReplicas(), redis.getWaitTimeout(), commandTimeout, MachineProperties.PREFIX + ".distributor.redis"));
    }
    
    /**
     * The distribution at startup, whose first command also pays for connecting, is bounded by
     * {@code cosid.machine.distributor.redis.distribute-timeout}; guard, revert and every re-claim, which run under a lock,
     * by {@code cosid.machine.distributor.redis.timeout}.
     */
    @Bean
    @ConditionalOnMissingBean(MachineIdDistributor.class)
    @ConditionalOnBuiltInMachineStore
    public LeaseStoreMachineIdDistributor springRedisMachineIdDistributor(SpringRedisMachineLeaseStore springRedisMachineLeaseStore,
                                                                          MachineStateStorage localMachineState,
                                                                          ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        MachineProperties.Redis redis = machineProperties.getDistributor().getRedis();
        SpringRedisMachineLeaseStore distributeStore = springRedisMachineLeaseStore.withCommandTimeout(RedisCommandTimeout.of(
            MachineLeaseStores.positive(redis.getDistributeTimeout(), MachineProperties.PREFIX + ".distributor.redis.distribute-timeout")));
        return new LeaseStoreMachineIdDistributor(new MachineLeaseManager(distributeStore), new MachineLeaseManager(springRedisMachineLeaseStore),
            localMachineState, clockBackwardsSynchronizer);
    }

    /**
     * The Redis backend for {@link me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider} selection.
     */
    @Bean
    public BuiltInStoreProvider cosIdRedisMachineStoreProvider(ObjectProvider<StringRedisTemplate> redisTemplate) {
        return BuiltInStoreProvider.machine("redis", context -> springRedisMachineIdDistributor(springRedisMachineLeaseStore(redisTemplate.getObject()),
            context.getMachineStateStorage(), context.getClockBackwardsSynchronizer()));
    }
    
    /**
     * The replica wait of {@code prefix.wait-replicas} and {@code prefix.wait-timeout}, which must end before the command
     * timeout that bounds it.
     */
    static RedisReplicaWait replicaWait(int replicas, Duration waitTimeout, RedisCommandTimeout commandTimeout, String prefix) {
        RedisReplicaWait replicaWait = RedisReplicaWait.of(replicas, waitTimeout);
        if (replicaWait.isEnabled() && commandTimeout.isEnabled() && replicaWait.getTimeout().compareTo(commandTimeout.getTimeout()) >= 0) {
            throw new IllegalStateException(prefix + ".wait-timeout [" + waitTimeout + "] must be shorter than " + prefix + ".timeout ["
                + commandTimeout.getTimeout() + "], which bounds the write and its wait.");
        }
        return replicaWait;
    }
}
