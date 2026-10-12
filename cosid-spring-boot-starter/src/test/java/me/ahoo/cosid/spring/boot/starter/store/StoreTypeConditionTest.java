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

import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class StoreTypeConditionTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(StatefulSetOnly.class, ManualOnly.class, RedisSegmentOnly.class);
    
    @Test
    void unsetTypesAreManualAndRedis() {
        contextRunner.run(context -> assertThat(context)
            .hasBean("manual")
            .doesNotHaveBean("statefulSet")
            .hasBean("redisSegment"));
    }
    
    @Test
    void typesIgnoreCaseAndDashes() {
        for (String type : new String[] {"stateful_set", "stateful-set", "STATEFUL_SET", " Stateful-Set "}) {
            contextRunner
                .withPropertyValues(MachineProperties.Distributor.TYPE + "=" + type, SegmentIdProperties.Distributor.TYPE + "=REDIS")
                .run(context -> assertThat(context)
                    .hasBean("statefulSet")
                    .doesNotHaveBean("manual")
                    .hasBean("redisSegment"));
        }
    }
    
    @Test
    void otherTypesMatchNothingBuiltIn() {
        contextRunner
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=etcd", SegmentIdProperties.Distributor.TYPE + "=etcd")
            .run(context -> assertThat(context)
                .doesNotHaveBean("statefulSet")
                .doesNotHaveBean("manual")
                .doesNotHaveBean("redisSegment"));
    }
    
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMachineStoreType(MachineProperties.Distributor.STATEFUL_SET)
    static class StatefulSetOnly {
        @Bean
        String statefulSet() {
            return "statefulSet";
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMachineStoreType(MachineProperties.Distributor.MANUAL)
    static class ManualOnly {
        @Bean
        String manual() {
            return "manual";
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnSegmentStoreType(SegmentIdProperties.Distributor.REDIS)
    static class RedisSegmentOnly {
        @Bean
        String redisSegment() {
            return "redisSegment";
        }
    }
}
