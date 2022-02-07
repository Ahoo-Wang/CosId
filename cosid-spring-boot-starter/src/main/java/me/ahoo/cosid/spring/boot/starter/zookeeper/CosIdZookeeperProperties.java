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

import me.ahoo.cosid.CosId;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * CosId Zookeeper Properties.
 *
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = CosIdZookeeperProperties.PREFIX)
public class CosIdZookeeperProperties {
    public static final String PREFIX = CosId.COSID_PREFIX + "zookeeper";

    private boolean enabled = true;

    private String connectString = "localhost:2181";

    private Retry retry = new Retry();

    private Duration blockUntilConnectedWait = Duration.ofSeconds(10);

    private Duration sessionTimeout = Duration.ofSeconds(60);

    private Duration connectionTimeout = Duration.ofSeconds(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConnectString() {
        return connectString;
    }

    public void setConnectString(String connectString) {
        this.connectString = connectString;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public Duration getBlockUntilConnectedWait() {
        return blockUntilConnectedWait;
    }

    public void setBlockUntilConnectedWait(Duration blockUntilConnectedWait) {
        this.blockUntilConnectedWait = blockUntilConnectedWait;
    }

    public Duration getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(Duration sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public static class Retry {

        private int baseSleepTimeMs = 100;

        private int maxRetries = 5;

        private int maxSleepMs = 500;

        public int getBaseSleepTimeMs() {
            return baseSleepTimeMs;
        }

        public void setBaseSleepTimeMs(int baseSleepTimeMs) {
            this.baseSleepTimeMs = baseSleepTimeMs;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getMaxSleepMs() {
            return maxSleepMs;
        }

        public void setMaxSleepMs(int maxSleepMs) {
            this.maxSleepMs = maxSleepMs;
        }
    }
}
