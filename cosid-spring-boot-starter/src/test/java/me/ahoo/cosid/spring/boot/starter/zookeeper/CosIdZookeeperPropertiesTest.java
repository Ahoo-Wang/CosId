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

package me.ahoo.cosid.spring.boot.starter.zookeeper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

class CosIdZookeeperPropertiesTest {

    @Test
    void defaultsKeepZookeeperSupportEnabledButPointAtLocalhost() {
        CosIdZookeeperProperties properties = new CosIdZookeeperProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getConnectString()).isEqualTo("localhost:2181");
        assertThat(properties.getBlockUntilConnectedWait()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getSessionTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.getRetry().getBaseSleepTimeMs()).isEqualTo(100);
        assertThat(properties.getRetry().getMaxRetries()).isEqualTo(5);
        assertThat(properties.getRetry().getMaxSleepMs()).isEqualTo(500);
    }

    @Test
    void binderMapsDurationsAndRetrySettings() {
        CosIdZookeeperProperties properties = bind(Map.of(
            "cosid.zookeeper.enabled", "false",
            "cosid.zookeeper.connect-string", "zk-1:2181,zk-2:2181",
            "cosid.zookeeper.block-until-connected-wait", "2s",
            "cosid.zookeeper.session-timeout", "30s",
            "cosid.zookeeper.connection-timeout", "3s",
            "cosid.zookeeper.retry.base-sleep-time-ms", "50",
            "cosid.zookeeper.retry.max-retries", "7",
            "cosid.zookeeper.retry.max-sleep-ms", "1000"
        ));

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getConnectString()).isEqualTo("zk-1:2181,zk-2:2181");
        assertThat(properties.getBlockUntilConnectedWait()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getSessionTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getRetry().getBaseSleepTimeMs()).isEqualTo(50);
        assertThat(properties.getRetry().getMaxRetries()).isEqualTo(7);
        assertThat(properties.getRetry().getMaxSleepMs()).isEqualTo(1000);
    }

    private static CosIdZookeeperProperties bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
            .bind("cosid.zookeeper", CosIdZookeeperProperties.class)
            .get();
    }
}
