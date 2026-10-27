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

package me.ahoo.cosid.store.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import me.ahoo.cosid.segment.Range;
import me.ahoo.cosid.segment.RangeStore;
import me.ahoo.cosid.store.Store;
import me.ahoo.cosid.store.tck.RangeStoreContract;

import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

/**
 * The range contract on a primary with one replica: {@code wait=auto} runs {@code WAIT 1} after every write.
 */
class ReplicatedRangeStoreTest extends RangeStoreContract {
    @Override
    protected RangeStore newStore() {
        return RedisServers.open(RedisServers.sharedReplicated().primary(), Map.of()).ranges();
    }

    /** An acknowledged reserve is on the replica when it returns. */
    @Test
    void acknowledgedReserveIsOnTheReplica() {
        RedisServers.Replicated pair = RedisServers.sharedReplicated();
        String key = "replicated-" + UUID.randomUUID();
        try (Store store = RedisServers.open(pair.primary(), Map.of()); StatefulRedisConnection<String, String> replica = pair.replica().connect()) {
            for (int i = 0; i < 20; i++) {
                Range range = store.ranges().reserve(key, 10, 0);
                assertEquals(Long.toString(range.end()), replica.sync().get(RedisStore.rangeKey(key)), "the replica has the write of " + range);
            }
        }
    }
}
