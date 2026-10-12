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

package me.ahoo.cosid.spring.redis;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.lease.Lease;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * A Redis stall longer than the renewal timeout: claims, bounded by the claim timeout, still go through; renewals do not.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpringRedisMachineLeaseStoreStallTest {
    private static final long STALL_MILLIS = 1500;
    private LettuceConnectionFactory storeConnections;
    private LettuceConnectionFactory adminConnections;
    private SpringRedisMachineLeaseStore store;
    private StringRedisTemplate admin;
    
    @BeforeAll
    void setup() {
        storeConnections = RealRedis.createConnectionFactory();
        adminConnections = RealRedis.createConnectionFactory();
        store = new SpringRedisMachineLeaseStore(RealRedis.createRedisTemplate(storeConnections),
            RedisCommandTimeout.of(Duration.ofSeconds(1)), RedisCommandTimeout.of(Duration.ofSeconds(10)));
        admin = RealRedis.createRedisTemplate(adminConnections);
    }
    
    @AfterAll
    void destroy() {
        if (storeConnections != null) {
            storeConnections.destroy();
        }
        if (adminConnections != null) {
            adminConnections.destroy();
        }
    }
    
    private void stallRedis() {
        // DEBUG SLEEP is disabled on Redis 7: CLIENT PAUSE holds every client's commands instead.
        admin.execute((RedisCallback<Object>) connection -> connection.execute("CLIENT",
            "PAUSE".getBytes(StandardCharsets.UTF_8), String.valueOf(STALL_MILLIS).getBytes(StandardCharsets.UTF_8)));
    }
    
    @Test
    void claimSurvivesAStallLongerThanTheRenewalTimeout() {
        String namespace = "stall_" + MockIdGenerator.INSTANCE.generateAsString();
        stallRedis();
        long start = System.nanoTime();
        
        Lease lease = store.claimNew(namespace, InstanceId.of("owner", false), 3, store.serverTime()).orElseThrow();
        
        assertThat(lease.getMachineId(), equalTo(0));
        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis(), greaterThanOrEqualTo(STALL_MILLIS - 200));
    }
    
    @Test
    void renewalGivesUpOnAStallLongerThanItsTimeout() throws InterruptedException {
        String namespace = "stall_" + MockIdGenerator.INSTANCE.generateAsString();
        Lease lease = store.claimNew(namespace, InstanceId.of("owner", false), 3, store.serverTime()).orElseThrow();
        stallRedis();
        
        assertThrows(RuntimeException.class, () -> store.renew(lease, lease.getRenewedAt() + 1));
        Thread.sleep(STALL_MILLIS);
    }
}
