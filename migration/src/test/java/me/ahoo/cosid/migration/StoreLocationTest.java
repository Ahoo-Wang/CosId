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
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.migration.source.MongoSource;

import org.junit.jupiter.api.Test;

import java.util.Map;

class StoreLocationTest {
    @Test
    void theSchemeGivesTheType() {
        assertThat(StoreLocation.parse("jdbc:mysql://db/cosid", Map.of()).type(), is(StoreLocation.JDBC));
        assertThat(StoreLocation.parse("redis://localhost:6379", Map.of()).type(), is(StoreLocation.REDIS));
        assertThat(StoreLocation.parse("rediss://localhost:6379", Map.of()).type(), is(StoreLocation.REDIS));
        assertThat(StoreLocation.parse("mongodb+srv://cluster/", Map.of()).type(), is(StoreLocation.MONGO));
        assertThat(StoreLocation.parse("zookeeper://zk:2181", Map.of()).type(), is(StoreLocation.ZOOKEEPER));
        assertThrows(IllegalArgumentException.class, () -> StoreLocation.parse("http://localhost", Map.of()));
    }

    @Test
    void theAccountIsUserOrUsernameAsInTheJdbcStore() {
        assertThat(StoreLocation.parse("jdbc:h2:mem:x", Map.of("user", "sa")).user(), is("sa"));
        assertThat(StoreLocation.parse("jdbc:h2:mem:x", Map.of("username", "sa")).user(), is("sa"));
        assertThat(StoreLocation.parse("jdbc:h2:mem:x", Map.of("user", "sa", "username", "sa")).user(), is("sa"));
        assertThat(StoreLocation.parse("jdbc:h2:mem:x", Map.of()).user(), is((String) null));
        StoreLocation both = StoreLocation.parse("jdbc:h2:mem:x", Map.of("user", "sa", "username", "root"));
        assertThrows(IllegalArgumentException.class, both::user);
    }

    @Test
    void passwordsStayOutOfThePlan() {
        StoreLocation location = StoreLocation.parse("jdbc:h2:mem:x", Map.of("user", "sa", "password", "secret"));
        assertThat(location.redacted().options(), is(Map.of("user", "sa")));
        assertThat(location.withOptions(Map.of("user", "root")).option("user"), is("root"));
        assertThat(location.withOptions(Map.of()), is(location));
        assertThat(location.withEnvironmentPassword("FROM").option("password"), is("secret"));
        assertThat(location.toString(), is("jdbc jdbc:h2:mem:x"));
    }

    @Test
    void urlsLoseTheirSecrets() {
        assertThat(StoreLocation.redactUrl("mongodb://cosid:s3cret@db1,db2/cosid_db?authSource=admin"), is("mongodb://cosid@db1,db2/cosid_db?authSource=admin"));
        assertThat(StoreLocation.redactUrl("redis://:s3cret@cache:6379"), is("redis://@cache:6379"));
        assertThat(StoreLocation.redactUrl("jdbc:mysql://db/cosid?user=root&password=s3cret&useSSL=true"), is("jdbc:mysql://db/cosid?user=root&useSSL=true"));
        assertThat(StoreLocation.redactUrl("jdbc:mysql://db/cosid?password=s3cret"), is("jdbc:mysql://db/cosid"));
        assertThat(StoreLocation.redactUrl("jdbc:sqlserver://db;user=sa;password=s3cret;encrypt=false"), is("jdbc:sqlserver://db;user=sa;encrypt=false"));
        assertThat(StoreLocation.redactUrl("jdbc:h2:mem:x;PWD=s3cret"), is("jdbc:h2:mem:x"));
        assertThat(StoreLocation.redactUrl("zookeeper://zk:2181/cosid"), is("zookeeper://zk:2181/cosid"));
        StoreLocation location = StoreLocation.parse("mongodb://cosid:s3cret@db/cosid_db", Map.of("password", "s3cret"));
        assertThat(location.redacted().url(), is("mongodb://cosid@db/cosid_db"));
        assertThat(location.redacted().options(), is(Map.of()));
        // R1-mig-01: what messages print.
        assertThat(location.toString(), is("mongo mongodb://cosid@db/cosid_db"));
    }

    @Test
    void mongoTakesItsCredentialFromOptions() {
        // MongoClients connect lazily: building the client checks the settings only.
        MongoSource.client(StoreLocation.parse("mongodb://cosid@localhost:1/cosid_db", Map.of("password", "s3cret"))).close();
        MongoSource.client(StoreLocation.parse("mongodb://localhost:1/", Map.of("user", "cosid", "password", "s3cret", "authSource", "admin"))).close();
        assertThrows(IllegalArgumentException.class, () -> MongoSource.client(StoreLocation.parse("mongodb://localhost:1/", Map.of("password", "s3cret"))));
    }
}
