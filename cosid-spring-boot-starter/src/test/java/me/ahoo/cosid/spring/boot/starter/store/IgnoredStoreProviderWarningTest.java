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

import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.segment.ConditionalOnCosIdSegmentEnabled;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdSpringRedisSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributorFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(OutputCaptureExtension.class)
class IgnoredStoreProviderWarningTest {
    private static final IdSegmentDistributorFactory CUSTOM_FACTORY = mock(IdSegmentDistributorFactory.class);
    
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
        .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", SegmentIdProperties.Distributor.TYPE + "=redis");
    
    static CosIdStoreProvider customRedisProvider() {
        return new CosIdStoreProvider() {
            @Override
            public String getType() {
                return "redis";
            }
            
            @Override
            public IdSegmentDistributorFactory createIdSegmentDistributorFactory(SegmentStoreContext context) {
                return CUSTOM_FACTORY;
            }
        };
    }
    
    /**
     * Registered after the Redis backend checked for application providers.
     */
    @AutoConfiguration(after = CosIdSpringRedisSegmentAutoConfiguration.class)
    static class LateProviderAutoConfiguration {
        @Bean
        CosIdStoreProvider lateRedisProvider() {
            return customRedisProvider();
        }
    }
    
    @Test
    void warnsWhenALateProviderIsIgnored(CapturedOutput output) {
        contextRunner
            .withConfiguration(AutoConfigurations.of(CosIdSpringRedisSegmentAutoConfiguration.class, CosIdSegmentStoreAutoConfiguration.class,
                LateProviderAutoConfiguration.class))
            .run(context -> {
                assertThat(context.getBean(IdSegmentDistributorFactory.class)).isInstanceOf(SpringRedisIdSegmentDistributorFactory.class);
                assertThat(output).contains("CosIdStoreProvider [lateRedisProvider] of type [redis] is ignored");
            });
    }
    
    @Test
    void doesNotWarnWhenTheProviderIsUsed(CapturedOutput output) {
        contextRunner
            .withConfiguration(AutoConfigurations.of(CosIdSpringRedisSegmentAutoConfiguration.class, CosIdSegmentStoreAutoConfiguration.class))
            .withBean("earlyRedisProvider", CosIdStoreProvider.class, IgnoredStoreProviderWarningTest::customRedisProvider)
            .run(context -> {
                assertThat(context.getBean(IdSegmentDistributorFactory.class)).isSameAs(CUSTOM_FACTORY);
                assertThat(output).doesNotContain("is ignored");
            });
    }
}
