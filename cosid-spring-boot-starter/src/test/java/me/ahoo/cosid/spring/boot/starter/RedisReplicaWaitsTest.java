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

package me.ahoo.cosid.spring.boot.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import me.ahoo.cosid.spring.redis.RedisCommandTimeout;
import me.ahoo.cosid.spring.redis.RedisReplicaWait;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(OutputCaptureExtension.class)
class RedisReplicaWaitsTest {
    private static final String PREFIX = "cosid.machine.distributor.redis";
    private static final Duration WAIT_TIMEOUT = Duration.ofMillis(500);

    private static Map<String, RedisCommandTimeout> timeouts(Duration timeout, Duration distributeTimeout) {
        Map<String, RedisCommandTimeout> timeouts = new LinkedHashMap<>();
        timeouts.put(PREFIX + ".timeout", RedisCommandTimeout.of(timeout));
        timeouts.put(PREFIX + ".distribute-timeout", RedisCommandTimeout.of(distributeTimeout));
        return timeouts;
    }

    private static LettuceConnectionFactory lettuce(Duration commandTimeout, boolean shareNativeConnection) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(),
            LettuceClientConfiguration.builder().commandTimeout(commandTimeout).build());
        factory.setShareNativeConnection(shareNativeConnection);
        return factory;
    }

    @Test
    void noWaitChecksNothing(CapturedOutput output) {
        assertThat(RedisReplicaWaits.of(PREFIX, 0, Duration.ofSeconds(9), lettuce(Duration.ofMillis(1), true),
            timeouts(Duration.ofMillis(1), Duration.ofMillis(1)))).isSameAs(RedisReplicaWait.NONE);
        assertThat(output).doesNotContain("shares one native connection");
    }

    @Test
    void waitTimeoutMustBeShorterThanEachBoundingTimeout() {
        assertThatThrownBy(() -> RedisReplicaWaits.of(PREFIX, 1, WAIT_TIMEOUT, null, timeouts(WAIT_TIMEOUT, Duration.ofSeconds(10))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(PREFIX + ".wait-timeout").hasMessageContaining(PREFIX + ".timeout [");
        assertThatThrownBy(() -> RedisReplicaWaits.of(PREFIX, 1, WAIT_TIMEOUT, null, timeouts(Duration.ofSeconds(1), Duration.ofMillis(400))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(PREFIX + ".distribute-timeout");
        // A disabled timeout does not bound the wait.
        assertThat(RedisReplicaWaits.of(PREFIX, 1, WAIT_TIMEOUT, null, timeouts(Duration.ZERO, Duration.ofSeconds(10))).getReplicas()).isEqualTo(1);
    }

    @Test
    void waitTimeoutMustBeShorterThanTheLettuceCommandTimeout() {
        assertThatThrownBy(() -> RedisReplicaWaits.of(PREFIX, 1, WAIT_TIMEOUT, lettuce(WAIT_TIMEOUT, false), Map.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(PREFIX + ".wait-timeout").hasMessageContaining("spring.data.redis.timeout");
    }

    @Test
    void warnsWhenLettuceSharesItsNativeConnection(CapturedOutput output) {
        RedisReplicaWaits.of(PREFIX, 1, WAIT_TIMEOUT, lettuce(Duration.ofSeconds(60), true), Map.of());
        assertThat(output).contains("WARN").contains(PREFIX + ".wait-replicas=1").contains("shares one native connection")
            .contains("setShareNativeConnection(false)");
    }

    @Test
    void doesNotWarnWithoutTheSharedNativeConnection(CapturedOutput output) {
        RedisReplicaWait replicaWait = RedisReplicaWaits.of(PREFIX, 1, WAIT_TIMEOUT, lettuce(Duration.ofSeconds(60), false), Map.of());
        assertThat(replicaWait.getTimeout()).isEqualTo(WAIT_TIMEOUT);
        assertThat(output).doesNotContain("shares one native connection");
    }

    @Test
    void jedisCanNotWaitOnARedisCluster() {
        JedisConnectionFactory cluster = new JedisConnectionFactory(new RedisClusterConfiguration(List.of("localhost:7000")));
        assertThatThrownBy(() -> RedisReplicaWaits.of(PREFIX, 1, WAIT_TIMEOUT, cluster, Map.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(PREFIX + ".wait-replicas needs Lettuce");
        assertThat(RedisReplicaWaits.of(PREFIX, 1, WAIT_TIMEOUT, new JedisConnectionFactory(), Map.of()).getReplicas()).isEqualTo(1);
    }
}
