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
import me.ahoo.cosid.proxy.api.SegmentClient;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdProxySegmentAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdProxySegmentAutoConfiguration.class))
        .withBean(SegmentClient.class, () -> mock(SegmentClient.class));

    @Test
    void createsProxySegmentDistributorFactoryWhenTypeIsProxy() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=proxy")
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(CosIdProxySegmentAutoConfiguration.class)
                    .hasSingleBean(IdSegmentDistributorFactory.class)
                ;
            });
    }

    @Test
    void backsOffWhenUserProvidesSegmentDistributorFactory() {
        IdSegmentDistributorFactory userFactory = definition -> mock(me.ahoo.cosid.segment.IdSegmentDistributor.class);

        this.contextRunner
            .withBean(IdSegmentDistributorFactory.class, () -> userFactory)
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=proxy")
            .run(context -> assertThat(context)
                .hasSingleBean(IdSegmentDistributorFactory.class)
                .getBean(IdSegmentDistributorFactory.class)
                .isSameAs(userFactory));
    }

    @Test
    void doesNotCreateFactoryWhenSegmentIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=proxy")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdProxySegmentAutoConfiguration.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }

    @Test
    void doesNotCreateFactoryWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=jdbc")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdProxySegmentAutoConfiguration.class)
                .doesNotHaveBean(IdSegmentDistributorFactory.class));
    }

    @Test
    void failsFastWhenCoApiRestClientBuilderIsMissing() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdProxySegmentAutoConfiguration.class))
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=proxy")
            .run(context -> assertThat(hasCauseMessage(context.getStartupFailure(), "RestClient$Builder"))
                .isTrue());
    }

    private static boolean hasCauseMessage(Throwable throwable, String message) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
