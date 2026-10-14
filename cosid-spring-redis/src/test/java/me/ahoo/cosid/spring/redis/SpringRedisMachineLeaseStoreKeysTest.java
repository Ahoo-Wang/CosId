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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;

import me.ahoo.cosid.machine.InstanceId;

import io.lettuce.core.cluster.SlotHash;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The lease script declares every key it touches, all in one Redis Cluster slot; the names are those of 3.4 and 4.0.
 */
class SpringRedisMachineLeaseStoreKeysTest {
    
    @Test
    void theScriptKeysOfANamespace() {
        List<String> keys = SpringRedisMachineLeaseStore.keys("order-service");
        assertThat(keys, contains("cosid:{order-service}:itc_idx", "cosid:{order-service}:revert", "cosid:{order-service}:adder",
            "cosid:{order-service}:stable", "cosid:{order-service}:lease"));
        List<Integer> slots = keys.stream().map(SlotHash::getSlot).collect(Collectors.toList());
        assertThat(slots, everyItem(is(SlotHash.getSlot("order-service"))));
    }
    
    /**
     * Captures the keys the store passes to its script, without Redis.
     */
    static final class RecordingRedisTemplate extends StringRedisTemplate {
        List<String> keys;
        
        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            this.keys = keys;
            return (T) List.of(3L, 100L, 100L, 1L);
        }
    }
    
    @Test
    void theScriptReceivesTheFiveKeys() {
        RecordingRedisTemplate redisTemplate = new RecordingRedisTemplate();
        SpringRedisMachineLeaseStore store = new SpringRedisMachineLeaseStore(redisTemplate, RedisCommandTimeout.of(Duration.ofSeconds(1)));
        
        store.claimNew("order-service", InstanceId.of("instance", false), 7, 100);
        
        assertThat(redisTemplate.keys, contains("cosid:{order-service}:itc_idx", "cosid:{order-service}:revert", "cosid:{order-service}:adder",
            "cosid:{order-service}:stable", "cosid:{order-service}:lease"));
    }
}
