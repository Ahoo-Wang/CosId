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

package me.ahoo.cosid.proxy.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import me.ahoo.cosid.proxy.server.configuration.ProxyAuthConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.server.PathContainer;

import java.util.List;

class ProxyAuthPublicPathsTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(ProxyAuthConfiguration.class)
        .withPropertyValues(ProxyAuthProperties.PREFIX + ".tokens[0]=secret");
    
    private static boolean isPublic(ProxyAuthWebFilter filter, String path) {
        return filter.isPublic(PathContainer.parsePath(path));
    }
    
    @Test
    void defaultsToActuatorHealth() {
        contextRunner.run(context -> {
            ProxyAuthWebFilter filter = context.getBean(ProxyAuthWebFilter.class);
            assertThat(isPublic(filter, "/actuator/health")).isTrue();
            assertThat(isPublic(filter, "/actuator/health/liveness")).isTrue();
            assertThat(isPublic(filter, "/actuator/cosid")).isFalse();
        });
    }
    
    @Test
    void followsCustomActuatorBasePath() {
        contextRunner
            .withPropertyValues("management.endpoints.web.base-path=/manage")
            .run(context -> {
                ProxyAuthWebFilter filter = context.getBean(ProxyAuthWebFilter.class);
                assertThat(isPublic(filter, "/manage/health")).isTrue();
                assertThat(isPublic(filter, "/manage/health/readiness")).isTrue();
                assertThat(isPublic(filter, "/actuator/health")).isFalse();
            });
    }
    
    @Test
    void explicitPublicPathsWin() {
        contextRunner
            .withPropertyValues("management.endpoints.web.base-path=/manage", ProxyAuthProperties.PREFIX + ".public-paths[0]=/v3/api-docs/**")
            .run(context -> {
                ProxyAuthWebFilter filter = context.getBean(ProxyAuthWebFilter.class);
                assertThat(isPublic(filter, "/v3/api-docs/swagger-config")).isTrue();
                assertThat(isPublic(filter, "/manage/health")).isFalse();
            });
    }
    
    @Test
    void healthPathsNormalizeTheBasePath() {
        assertThat(ProxyAuthProperties.healthPaths("/")).isEqualTo(List.of("/health", "/health/**"));
        assertThat(ProxyAuthProperties.healthPaths("")).isEqualTo(List.of("/health", "/health/**"));
        assertThat(ProxyAuthProperties.healthPaths("manage/")).isEqualTo(List.of("/manage/health", "/manage/health/**"));
    }
}
