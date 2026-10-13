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

package me.ahoo.cosid.spring.boot.starter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.Decorator;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.GuardDistribute;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.ManualMachineIdDistributor;
import me.ahoo.cosid.segment.CountedSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.SegmentId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.segment.ConditionalOnCosIdSegmentEnabled;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;
import me.ahoo.cosid.spring.boot.starter.snowflake.ConditionalOnCosIdSnowflakeEnabled;
import me.ahoo.cosid.spring.boot.starter.snowflake.CosIdSnowflakeAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.snowflake.SnowflakeIdProperties;
import me.ahoo.cosid.test.segment.AtomicIdSegmentDistributor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdMetricsAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class, CosIdSegmentAutoConfiguration.class,
            CosIdSnowflakeAutoConfiguration.class, CosIdMetricsAutoConfiguration.class))
        .withBean(IdSegmentDistributorFactory.class, () -> definition -> new AtomicIdSegmentDistributor())
        .withBean(MachineProperties.class, MachineProperties::new)
        .withBean(InstanceId.class, () -> InstanceId.of("metrics-test", false))
        .withBean(GuardDistribute.class, () -> new GuardDistribute(
            new ManualMachineIdDistributor(1, MachineStateStorage.IN_MEMORY, ClockBackwardsSynchronizer.DEFAULT), MachineIdGuarder.NONE))
        .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .withPropertyValues("cosid.provider.isolated=true",
            ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", SegmentIdProperties.PREFIX + ".share.enabled=false",
            SegmentIdProperties.PREFIX + ".provider.order.step=10",
            ConditionalOnCosIdSnowflakeEnabled.ENABLED_KEY + "=true", SnowflakeIdProperties.PREFIX + ".share.enabled=false",
            SnowflakeIdProperties.PREFIX + ".provider.snowflake.epoch=1577203200000");
    
    @Test
    void registersTheBinderByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(CosIdMeterBinder.class);
            context.getBean(CosIdMeterBinder.class).bindTo(context.getBean(MeterRegistry.class));
            MeterRegistry registry = context.getBean(MeterRegistry.class);
            assertThat(registry.get("cosid.segment.remaining").tag("name", "order").gauge()).isNotNull();
            assertThat(registry.find("cosid.generator.generated").meters()).isEmpty();
            // Off by default: the generators are not wrapped.
            assertThat(context.getBean("orderSegmentId", SegmentId.class)).isNotInstanceOf(CountedSegmentId.class);
            assertThat(Decorator.getActual(context.getBean("orderSegmentId", SegmentId.class))).isInstanceOf(SegmentChainId.class);
        });
    }
    
    @Test
    void theGeneratedCountKeepsTheInjectedTypes() {
        contextRunner.withPropertyValues(CosIdMetricsProperties.GENERATED_ENABLED_KEY + "=true").run(context -> {
            assertThat(context).hasNotFailed();
            SegmentId segmentId = context.getBean("orderSegmentId", SegmentId.class);
            SnowflakeId snowflakeId = context.getBean("snowflakeSnowflakeId", SnowflakeId.class);
            assertThat(Decorator.getActual(segmentId)).isInstanceOf(SegmentChainId.class);
            segmentId.generate();
            segmentId.generateAsString();
            snowflakeId.generate();
            
            MeterRegistry registry = context.getBean(MeterRegistry.class);
            context.getBean(CosIdMeterBinder.class).bindTo(registry);
            assertThat(registry.get("cosid.generator.generated").tag("name", "order").functionCounter().count()).isEqualTo(2);
            assertThat(registry.get("cosid.generator.generated").tag("name", "snowflake").functionCounter().count()).isEqualTo(1);
        });
    }
    
    @Test
    void canBeTurnedOff() {
        contextRunner.withPropertyValues(CosIdMetricsProperties.ENABLED_KEY + "=false", CosIdMetricsProperties.GENERATED_ENABLED_KEY + "=true")
            .run(context -> {
                assertThat(context).hasNotFailed().doesNotHaveBean(CosIdMeterBinder.class);
                assertThat(context.getBean("orderSegmentId", SegmentId.class)).isNotInstanceOf(CountedSegmentId.class);
            });
    }
    
    @Test
    void staysOffWithoutMicrometer() {
        contextRunner.withClassLoader(new FilteredClassLoader("io.micrometer"))
            .withPropertyValues(CosIdMetricsProperties.GENERATED_ENABLED_KEY + "=true")
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(CosIdMeterBinder.class));
    }
}
