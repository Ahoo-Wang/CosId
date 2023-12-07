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

package me.ahoo.cosid.mongo;

import static org.hamcrest.MatcherAssert.assertThat;

import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.container.MongoLauncher;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MongoIdSegmentInitializerTest {
    MongoDatabase mongoDatabase;
    MongoIdSegmentInitializer idSegmentInitializer;
    MongoIdSegmentCollection cosIdSegmentCollection;
    
    @BeforeEach
    void setup() {
        mongoDatabase = MongoClients.create(MongoLauncher.getConnectionString()).getDatabase("cosid_db");
        idSegmentInitializer = new MongoIdSegmentInitializer(mongoDatabase);
        cosIdSegmentCollection = new MongoIdSegmentCollection(mongoDatabase.getCollection(IdSegmentCollection.COLLECTION_NAME));
    }
    
    @Test
    void ensureCosIdCollection() {
        idSegmentInitializer.ensureCosIdCollection();
    }
    
    @Test
    void ensureIdSegment() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        boolean actual = cosIdSegmentCollection.ensureIdSegment(namespace, 0);
        assertThat(actual, Matchers.equalTo(true));
        actual = cosIdSegmentCollection.ensureIdSegment(namespace, 0);
        assertThat(actual, Matchers.equalTo(false));
    }
}