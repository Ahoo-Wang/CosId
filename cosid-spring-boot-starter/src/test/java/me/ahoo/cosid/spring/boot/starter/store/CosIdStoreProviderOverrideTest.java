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

package me.ahoo.cosid.spring.boot.starter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.machine.ConditionalOnCosIdMachineEnabled;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdSpringRedisMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.segment.ConditionalOnCosIdSegmentEnabled;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdSpringRedisSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributorFactory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * An application provider overrides the built-in one of its type, or adds a new type selected with {@code provider}.
 */
class CosIdStoreProviderOverrideTest {
    private static final IdSegmentDistributorFactory CUSTOM_FACTORY = mock(IdSegmentDistributorFactory.class);
    private static final MachineIdDistributor CUSTOM_DISTRIBUTOR = mock(MachineIdDistributor.class);
    
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdSpringRedisSegmentAutoConfiguration.class, CosIdSpringRedisMachineIdDistributorAutoConfiguration.class,
            CosIdSegmentStoreAutoConfiguration.class, CosIdMachineStoreAutoConfiguration.class))
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
        .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
        .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true",
            SegmentIdProperties.Distributor.TYPE + "=redis", MachineProperties.Distributor.TYPE + "=redis");
    
    private static CosIdStoreProvider customProvider(String type) {
        return new CosIdStoreProvider() {
            @Override
            public String getType() {
                return type;
            }
            
            @Override
            public MachineIdDistributor createMachineIdDistributor(MachineStoreContext context) {
                return CUSTOM_DISTRIBUTOR;
            }
            
            @Override
            public IdSegmentDistributorFactory createIdSegmentDistributorFactory(SegmentStoreContext context) {
                return CUSTOM_FACTORY;
            }
        };
    }
    
    @Test
    void builtInProviderIsUsedByDefault() {
        contextRunner.run(context -> {
            assertThat(context.getBean("idSegmentDistributorFactory")).isInstanceOf(SpringRedisIdSegmentDistributorFactory.class);
            assertThat(context).hasSingleBean(IdSegmentDistributorFactory.class).doesNotHaveBean("cosIdIdSegmentDistributorFactory");
            assertThat(context).hasSingleBean(MachineIdDistributor.class).doesNotHaveBean("cosIdMachineIdDistributor");
        });
    }
    
    @Test
    void applicationProviderOverridesTheBuiltInOneOfItsType() {
        contextRunner
            .withBean("customRedisProvider", CosIdStoreProvider.class, () -> customProvider("redis"))
            .run(context -> {
                assertThat(context.getBean(IdSegmentDistributorFactory.class)).isSameAs(CUSTOM_FACTORY);
                assertThat(context.getBean(MachineIdDistributor.class)).isSameAs(CUSTOM_DISTRIBUTOR);
            });
    }
    
    @Test
    void applicationProviderOfANewTypeIsSelectedWithTheType() {
        contextRunner
            .withBean("etcdProvider", CosIdStoreProvider.class, () -> customProvider("etcd"))
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=ETCD", MachineProperties.Distributor.TYPE + "=etcd")
            .run(context -> {
                assertThat(context.getBean(IdSegmentDistributorFactory.class)).isSameAs(CUSTOM_FACTORY);
                assertThat(context.getBean(MachineIdDistributor.class)).isSameAs(CUSTOM_DISTRIBUTOR);
            });
    }
    
    @Test
    void unknownTypeFailsStartupNamingIt() {
        contextRunner
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=etcd")
            .run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("[etcd]"));
    }
    
    @Test
    void removedProviderPropertyFailsStartupWithAHint() {
        contextRunner
            .withPropertyValues(MachineProperties.Distributor.REMOVED_PROVIDER + "=etcd")
            .run(context -> assertThat(context).hasFailed()
                .getFailure().hasStackTraceContaining(MachineProperties.Distributor.TYPE + "=etcd"));
    }
}
