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

import io.lettuce.core.cluster.SlotHash;
import org.junit.jupiter.api.Test;

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
}
