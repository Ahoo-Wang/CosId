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

package me.ahoo.cosid.spring.boot.starter.segment;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.redis.RedisReplicaWait;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributor;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributorFactory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * CosIdSpringRedisSegmentAutoConfigurationTest .
 *
 * @author ahoo wang
 */
class CosIdSpringRedisSegmentAutoConfigurationTest {
    
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdSpringRedisSegmentAutoConfiguration.class))
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));
    
    @Test
    void createsRedisSegmentFactoryWithoutConnectingToRedis() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=redis")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdSpringRedisSegmentAutoConfiguration.class)
                    .hasSingleBean(SegmentIdProperties.class)
                    .hasSingleBean(IdSegmentDistributorFactory.class)
                ;
            });
    }

    @Test
    void wiresRedisTimeoutProperty() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=redis")
            .withPropertyValues("cosid.segment.distributor.redis.timeout=3s")
            .run(context -> {
                SpringRedisIdSegmentDistributorFactory factory = (SpringRedisIdSegmentDistributorFactory) context.getBean(IdSegmentDistributorFactory.class);
                assertThat(factory.getCommandTimeout().getTimeout()).isEqualTo(Duration.ofSeconds(3));
            });
    }

    @Test
    void backsOffWhenUserProvidesDistributorFactory() {
        IdSegmentDistributorFactory factory = definition -> mock(me.ahoo.cosid.segment.IdSegmentDistributor.class);

        this.contextRunner
            .withBean(IdSegmentDistributorFactory.class, () -> factory)
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .hasSingleBean(IdSegmentDistributorFactory.class)
                .getBean(IdSegmentDistributorFactory.class)
                .isSameAs(factory));
    }

    @Test
    void doesNotCreateRedisSegmentFactoryWhenSegmentIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdSpringRedisSegmentAutoConfiguration.class)
                .doesNotHaveBean(SegmentIdProperties.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }

    @Test
    void doesNotCreateRedisSegmentFactoryWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=jdbc")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdSpringRedisSegmentAutoConfiguration.class)
                .doesNotHaveBean(SegmentIdProperties.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }
    
    @Test
    void doesNotCreateRedisSegmentFactoryWhenRedisDistributorClassIsMissing() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=redis")
            .withClassLoader(new FilteredClassLoader(SpringRedisIdSegmentDistributor.class))
            .run(context -> {
                assertThat(context)
                    .doesNotHaveBean(CosIdSpringRedisSegmentAutoConfiguration.class)
                    .doesNotHaveBean(SegmentIdProperties.class)
                    .doesNotHaveBean(IdSegmentDistributorFactory.class)
                ;
            });
    }
    
    @Test
    void noReplicaWaitByDefault() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", SegmentIdProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(((SpringRedisIdSegmentDistributorFactory) context.getBean(IdSegmentDistributorFactory.class)).getReplicaWait())
                .isSameAs(RedisReplicaWait.NONE));
    }
    
    @Test
    void wiresTheReplicaWait() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", SegmentIdProperties.Distributor.TYPE + "=redis",
                "cosid.segment.distributor.redis.wait-replicas=2", "cosid.segment.distributor.redis.wait-timeout=200ms")
            .run(context -> {
                RedisReplicaWait replicaWait = ((SpringRedisIdSegmentDistributorFactory) context.getBean(IdSegmentDistributorFactory.class)).getReplicaWait();
                assertThat(replicaWait.getReplicas()).isEqualTo(2);
                assertThat(replicaWait.getTimeout()).isEqualTo(Duration.ofMillis(200));
            });
    }
    
    @Test
    void aWaitTimeoutNotShorterThanTheTimeoutFailsStartup() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", SegmentIdProperties.Distributor.TYPE + "=redis",
                "cosid.segment.distributor.redis.wait-replicas=1", "cosid.segment.distributor.redis.wait-timeout=1s")
            .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("cosid.segment.distributor.redis.wait-timeout"));
    }
    
    @Test
    void aWaitTimeoutNotShorterThanTheLettuceCommandTimeoutFailsStartup() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        org.mockito.Mockito.when(redisTemplate.getConnectionFactory()).thenReturn(new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
            new org.springframework.data.redis.connection.RedisStandaloneConfiguration(),
            org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(300)).build()));
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdSpringRedisSegmentAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, () -> redisTemplate)
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", SegmentIdProperties.Distributor.TYPE + "=redis",
                "cosid.segment.distributor.redis.wait-replicas=1", "cosid.segment.distributor.redis.timeout=0")
            .run(context -> assertThat(context).getFailure().rootCause().hasMessageContaining("spring.data.redis.timeout"));
    }
}
