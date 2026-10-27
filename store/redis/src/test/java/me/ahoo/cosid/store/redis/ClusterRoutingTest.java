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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.lease.AcquireResult;
import me.ahoo.cosid.lease.Owner;
import me.ahoo.cosid.store.Store;

import io.lettuce.core.cluster.SlotHash;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Hash tags on Redis Cluster (06-stores.md §2.2): the keys of one range key or namespace share a slot, and different
 * ones spread over the primaries.
 */
class ClusterRoutingTest {
    @Test
    void keysOfOneGroupShareASlotAndGroupsSpreadOverTheNodes() {
        Set<String> rangeNodes = new HashSet<>();
        Set<String> namespaceNodes = new HashSet<>();
        try (Store store = RedisServers.open(RedisServers.cluster(), Map.of());
             StatefulRedisClusterConnection<String, String> connection = RedisServers.cluster().connect()) {
            for (int i = 0; i < 24; i++) {
                String key = "routing-" + UUID.randomUUID();
                store.ranges().reserve(key, 10, 0);
                store.ranges().seal(key);
                assertEquals(SlotHash.getSlot(RedisStore.rangeKey(key)), SlotHash.getSlot(RedisStore.sealedKey(key)));
                rangeNodes.add(nodeOf(connection, RedisStore.rangeKey(key)));

                String namespace = "routing-" + UUID.randomUUID();
                assertInstanceOf(AcquireResult.Acquired.class, store.slots().acquire(namespace, new Owner("host", UUID.randomUUID()), 4,
                    Duration.ofSeconds(60), Duration.ofSeconds(1), OptionalInt.empty()));
                Set<Integer> slots = new HashSet<>();
                for (String suffix : List.of("meta", "slot", "free", "live", "owner", "held")) {
                    String namespaceKey = RedisStore.namespaceKey(namespace, suffix);
                    slots.add(SlotHash.getSlot(namespaceKey));
                    assertEquals(1L, connection.sync().exists(namespaceKey), namespaceKey + " exists");
                }
                assertEquals(1, slots.size(), "one hash tag per namespace");
                namespaceNodes.add(nodeOf(connection, RedisStore.namespaceKey(namespace, "slot")));
            }
        }
        assertTrue(rangeNodes.size() > 1, "range keys spread over the primaries: " + rangeNodes);
        assertTrue(namespaceNodes.size() > 1, "namespaces spread over the primaries: " + namespaceNodes);
    }

    private static String nodeOf(StatefulRedisClusterConnection<String, String> connection, String key) {
        RedisClusterNode node = connection.getPartitions().getPartitionBySlot(SlotHash.getSlot(key));
        return node.getNodeId();
    }
}
