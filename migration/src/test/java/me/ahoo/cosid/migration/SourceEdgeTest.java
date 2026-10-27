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

package me.ahoo.cosid.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.migration.source.SourceReader;
import me.ahoo.cosid.migration.source.SourceSnapshot;
import me.ahoo.cosid.migration.source.Sources;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import org.apache.curator.framework.CuratorFramework;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

/** 4.x data the writers do not produce: odd nodes, a store of segments only, seals that cannot happen. */
class SourceEdgeTest {
    private static String namespace() {
        return "e" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void jdbcWithoutMachineTable() throws Exception {
        TestBackend.Store store = TestBackend.H2.store();
        String ns = namespace();
        try (Connection connection = DriverManager.getConnection(store.url()); Statement statement = connection.createStatement()) {
            statement.execute("create table cosid (name varchar(100) primary key, last_max_id bigint not null, last_fetch_time bigint not null)");
            statement.execute("insert into cosid values ('" + ns + ".order', 42, 0), ('" + ns + ".edge', " + (Long.MAX_VALUE - 5) + ", 0), ('other.order', 7, 0)");
        }
        try (SourceReader reader = Sources.open(store.location())) {
            SourceSnapshot snapshot = reader.read(ns);
            assertThat(snapshot.segments(), is(Map.of("order", 42L, "edge", Long.MAX_VALUE - 5)));
            assertThat(snapshot.machines().isEmpty(), is(true));
            assertThat(reader.seal(ns, "order", 100), is(142L));
            assertThrows(IllegalStateException.class, () -> reader.seal(ns, "missing", 100));
            IllegalStateException overflow = assertThrows(IllegalStateException.class, () -> reader.seal(ns, "edge", 100));
            assertThat(overflow.getMessage(), containsString("overflows"));
        }
        assertThrows(IllegalArgumentException.class, () -> Sources.open(StoreLocation.parse(store.url(), Map.of("segmentTable", "x;drop"))));
    }

    @Test
    void zookeeperOddNodes() throws Exception {
        TestBackend.Store store = TestBackend.ZOOKEEPER.store();
        String ns = namespace();
        try (CuratorFramework client = Zookeeper.open(store.location())) {
            client.create().creatingParentsIfNeeded().forPath("/cosid/" + ns + ".order", ByteBuffer.allocate(8).putLong(30).array());
            client.create().forPath("/cosid/" + ns + ".order-locker", new byte[0]);
            client.create().forPath("/cosid/" + ns + ".empty", new byte[0]);
            client.create().forPath("/cosid/" + ns + ".weird", "abc".getBytes(StandardCharsets.UTF_8));
            client.create().creatingParentsIfNeeded().forPath("/cosid/" + ns + "/__itc_idx/host-1", "1|abc".getBytes(StandardCharsets.UTF_8));
            client.create().creatingParentsIfNeeded().forPath("/cosid/" + ns + "/__revert/2", "2|1700000000000".getBytes(StandardCharsets.UTF_8));
            client.create().creatingParentsIfNeeded().forPath("/cosid/" + ns + "/__counter", new byte[]{0, 0, 0, 3});
        }
        try (SourceReader reader = Sources.open(store.location())) {
            SourceSnapshot snapshot = reader.read(ns);
            assertThat(snapshot.segments(), is(Map.of("order", 30L, "empty", 0L)));
            assertThat(snapshot.unrecognized(), hasItem(containsString(".weird")));
            assertThat(snapshot.unrecognized(), hasItem(containsString("unreadable machine state")));
            assertThat(snapshot.maxMachineTimestamp(), is(1700000000000L));
            assertThat(snapshot.machines().get("__counter"), is("00000003"));
            assertThat(reader.seal(ns, "empty", 10), is(10L));
            assertThrows(IllegalStateException.class, () -> reader.seal(ns, "missing", 10));
        }
    }

    @Test
    void redisOddKeys() {
        TestBackend.Store store = TestBackend.REDIS.store();
        String ns = namespace();
        RedisClient client = RedisClient.create(store.url());
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            connection.sync().set("cosid:{" + ns + ".bad}.adder", "abc");
            connection.sync().set("cosid:{" + ns + ".order}.adder", "20");
            connection.sync().hset("cosid:{" + ns + "}:itc_idx", "host-1", "0|1700000000000");
            connection.sync().hset("cosid:{" + ns + "}:revert", "1", "1700000000500");
            connection.sync().hset("cosid:{" + ns + "}:lease", "0", "1700000000100|1700000000900|5|1700000000000");
            connection.sync().set("cosid:{" + ns + "}:adder", "1");
            connection.sync().sadd("cosid:{" + ns + "}:stable", "host-1");
        } finally {
            client.shutdown();
        }
        try (SourceReader reader = Sources.open(store.location())) {
            SourceSnapshot snapshot = reader.read(ns);
            assertThat(snapshot.segments(), is(Map.of("order", 20L)));
            assertThat(snapshot.unrecognized(), hasItem(containsString(".bad}")));
            assertThat(snapshot.maxMachineTimestamp(), is(1700000000900L));
            assertThat(snapshot.machines().size(), is(5));
            assertThrows(IllegalStateException.class, () -> reader.seal(ns, "missing", 10));
        }
    }

    @Test
    void mongoOddDocuments() {
        TestBackend.Store store = TestBackend.MONGO.store();
        String ns = namespace();
        try (MongoClient client = MongoClients.create(store.url())) {
            client.getDatabase("cosid_db").getCollection("cosid").insertOne(new Document("_id", ns + ".odd").append("other", 1));
        }
        try (SourceReader reader = Sources.open(store.location())) {
            assertThat(reader.read(ns).unrecognized(), hasItem(containsString(ns + ".odd")));
            assertThrows(IllegalStateException.class, () -> reader.seal(ns, "odd", 10));
        }
    }
}
