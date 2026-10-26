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

import com.mongodb.WriteConcern;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

class MongoWriteConcernsTest {
    private static final String PROPERTY = "cosid.machine.distributor.mongo.write-concern";
    
    @Test
    void parsesNamesAndNumbers() {
        assertThat(MongoWriteConcerns.parse("majority", PROPERTY)).isEqualTo(WriteConcern.MAJORITY);
        assertThat(MongoWriteConcerns.parse("W1", PROPERTY)).isEqualTo(WriteConcern.W1);
        assertThat(MongoWriteConcerns.parse(" journaled ", PROPERTY)).isEqualTo(WriteConcern.JOURNALED);
        assertThat(MongoWriteConcerns.parse("2", PROPERTY)).isEqualTo(new WriteConcern(2));
        assertThatThrownBy(() -> MongoWriteConcerns.parse("most", PROPERTY)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(PROPERTY);
    }
    
    private static Document replicaSet(int members) {
        List<String> hosts = new java.util.ArrayList<>();
        for (int i = 0; i < members; i++) {
            hosts.add("mongo" + i + ":27017");
        }
        return new Document("setName", "rs0").append("hosts", hosts);
    }
    
    @Test
    void weakerThanMajorityOnlyOnAReplicaSet() {
        assertThat(MongoWriteConcerns.isWeakerThanMajority(WriteConcern.W1, replicaSet(3))).isTrue();
        assertThat(MongoWriteConcerns.isWeakerThanMajority(WriteConcern.UNACKNOWLEDGED, replicaSet(3))).isTrue();
        assertThat(MongoWriteConcerns.isWeakerThanMajority(WriteConcern.W2, replicaSet(3))).isFalse();
        assertThat(MongoWriteConcerns.isWeakerThanMajority(WriteConcern.W2, replicaSet(5))).isTrue();
        assertThat(MongoWriteConcerns.isWeakerThanMajority(WriteConcern.MAJORITY, replicaSet(3))).isFalse();
        // The server's default (majority since 5.0), a standalone server, a single member, nothing known.
        assertThat(MongoWriteConcerns.isWeakerThanMajority(WriteConcern.ACKNOWLEDGED, replicaSet(3))).isFalse();
        assertThat(MongoWriteConcerns.isWeakerThanMajority(WriteConcern.W1, new Document("isWritablePrimary", true))).isFalse();
        assertThat(MongoWriteConcerns.isWeakerThanMajority(WriteConcern.W1, replicaSet(1))).isFalse();
        assertThat(MongoWriteConcerns.isWeakerThanMajority(null, null)).isFalse();
    }
    
    @Test
    void passivesCountTowardsTheMajority() {
        // Two electable members and two priority 0 members: four data-bearing members, a majority of three.
        Document hello = replicaSet(2).append("passives", List.of("mongo2:27017", "mongo3:27017"));
        assertThat(MongoWriteConcerns.isWeakerThanMajority(WriteConcern.W2, hello)).isTrue();
        assertThat(MongoWriteConcerns.isWeakerThanMajority(WriteConcern.W3, hello)).isFalse();
    }
    
    @Test
    void theServersDefaultIsJudgedApart() {
        assertThat(MongoWriteConcerns.judge(WriteConcern.ACKNOWLEDGED, replicaSet(3))).isEqualTo(MongoWriteConcerns.Strength.SERVER_DEFAULT);
        assertThat(MongoWriteConcerns.judge(WriteConcern.ACKNOWLEDGED, replicaSet(2).append("arbiters", List.of("arbiter:27017"))))
            .isEqualTo(MongoWriteConcerns.Strength.SERVER_DEFAULT_WITH_ARBITERS);
        assertThat(MongoWriteConcerns.judge(WriteConcern.MAJORITY, replicaSet(2).append("arbiters", List.of("arbiter:27017"))))
            .isEqualTo(MongoWriteConcerns.Strength.MAJORITY);
        assertThat(MongoWriteConcerns.judge(WriteConcern.ACKNOWLEDGED, new Document("isWritablePrimary", true)))
            .isEqualTo(MongoWriteConcerns.Strength.NOT_APPLICABLE);
    }
}
