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

import me.ahoo.coapi.spring.client.sync.RestClientBuilderCustomizer;
import me.ahoo.cosid.proxy.api.SegmentClient;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.ProxyClientCondition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

class CosIdProxySegmentAutoConfigurationTest {
    private static final String COAP_PROXY_HOST = "cosid.proxy.host=http://localhost:8688";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdProxySegmentAutoConfiguration.class))
        .withBean(SegmentClient.class, () -> mock(SegmentClient.class))
        .withPropertyValues(COAP_PROXY_HOST);

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
            // spring-webflux is on the test classpath (for the WebClient timeout test): pin the blocking client mode.
            .withPropertyValues("coapi.mode=SYNC")
            .withPropertyValues(COAP_PROXY_HOST)
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

    private static ApplicationContextRunner plainRunner() {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdProxySegmentAutoConfiguration.class))
            .withPropertyValues(COAP_PROXY_HOST)
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=proxy");
    }

    @Test
    void httpInterfaceModeCreatesAPlainClientBean() {
        plainRunner()
            .withPropertyValues(ProxyClientCondition.CLIENT_KEY + "=http-interface")
            .run(context -> assertThat(context)
                .hasNotFailed()
                .hasBean("cosIdProxySegmentClient")
                .hasSingleBean(SegmentClient.class)
                .doesNotHaveBean(RestClientBuilderCustomizer.class)
                .hasSingleBean(IdSegmentDistributorFactory.class));
    }

    @Test
    void theUpperCaseValueSelectsTheHttpInterfaceModeToo() {
        plainRunner()
            .withPropertyValues(ProxyClientCondition.CLIENT_KEY + "=HTTP_INTERFACE")
            .run(context -> assertThat(context).hasNotFailed().hasBean("cosIdProxySegmentClient"));
    }

    @Test
    void withoutCoApiTheClientIsAPlainBean() {
        plainRunner()
            .withClassLoader(new FilteredClassLoader("me.ahoo.coapi.spring"))
            .run(context -> assertThat(context)
                .hasNotFailed()
                .hasBean("cosIdProxySegmentClient")
                .hasSingleBean(IdSegmentDistributorFactory.class));
    }

    @Test
    void coApiStaysTheDefault() {
        plainRunner()
            .withPropertyValues("coapi.mode=SYNC")
            .withBean(RestClient.Builder.class, RestClient::builder)
            .run(context -> assertThat(context)
                .hasNotFailed()
                .doesNotHaveBean("cosIdProxySegmentClient")
                .hasSingleBean(SegmentClient.class)
                .hasBean("cosIdProxyRestClientTimeoutCustomizer"));
    }

    @Test
    void aLoadBalancedHostFailsStartupWithPlainClients() {
        plainRunner()
            .withPropertyValues(ProxyClientCondition.CLIENT_KEY + "=http-interface", "cosid.proxy.host=lb://cosid-proxy")
            .withBean(me.ahoo.cosid.spring.boot.starter.CosIdProperties.class, () -> {
                me.ahoo.cosid.spring.boot.starter.CosIdProperties properties = new me.ahoo.cosid.spring.boot.starter.CosIdProperties();
                properties.getProxy().setHost("lb://cosid-proxy");
                return properties;
            })
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cosid.proxy.client=coapi"));
    }

    @Test
    void coApiAskedForButMissingFailsStartup() {
        plainRunner()
            .withClassLoader(new FilteredClassLoader("me.ahoo.coapi.spring"))
            .withPropertyValues(ProxyClientCondition.CLIENT_KEY + "=coapi")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CoApi is not on the classpath"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void thePlainClientsWarnWhenTheTokenGoesOverPlainHttp(CapturedOutput output) {
        plainRunner()
            // The bean is bound to the cosid.* properties, which override what the supplier set.
            .withPropertyValues(ProxyClientCondition.CLIENT_KEY + "=http-interface", "cosid.proxy.host=http://cosid-proxy:8688",
                "cosid.proxy.token=secret")
            .withBean(me.ahoo.cosid.spring.boot.starter.CosIdProperties.class, me.ahoo.cosid.spring.boot.starter.CosIdProperties::new)
            .run(context -> assertThat(context).hasNotFailed().hasBean("cosIdProxySegmentClient"));
        assertThat(output.getOut()).contains("[cosid.proxy.host:http://cosid-proxy:8688] is plain http");
    }
}
