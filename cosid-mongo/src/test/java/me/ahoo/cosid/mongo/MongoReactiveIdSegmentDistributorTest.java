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
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.mongo.reactive.MongoReactiveIdSegmentDistributorFactory;
import me.ahoo.cosid.mongo.reactive.MongoReactiveIdSegmentInitializer;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosid.test.container.MongoLauncher;
import me.ahoo.cosid.test.segment.distributor.IdSegmentDistributorSpec;

import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class MongoReactiveIdSegmentDistributorTest extends IdSegmentDistributorSpec {
    MongoDatabase mongoDatabase;
    IdSegmentDistributorFactory distributorFactory;
    MongoReactiveIdSegmentInitializer idSegmentInitializer;
    
    @BeforeEach
    void setup() {
        mongoDatabase = MongoClients.create(MongoLauncher.getConnectionString()).getDatabase("cosid_db");
        idSegmentInitializer = new MongoReactiveIdSegmentInitializer(mongoDatabase);
        
        idSegmentInitializer.ensureCosIdCollection();
        distributorFactory =
            new MongoReactiveIdSegmentDistributorFactory(mongoDatabase);
    }
    
    @Override
    protected IdSegmentDistributorFactory getFactory() {
        return distributorFactory;
    }
    
    @Override
    protected <T extends IdSegmentDistributor> void setMaxIdBack(T distributor, long maxId) {
    
    }
    
    @Override
    public void nextMaxIdWhenBack() {
    
    }
    
    @Test
    public void nextMaxIdInParallel() {
        var mono = Mono.fromRunnable(() -> {
            String namespace = MockIdGenerator.INSTANCE.generateAsString();
            IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "nextMaxIdIParallel", TEST_OFFSET, TEST_STEP);
            IdSegmentDistributor distributor = factory().create(definition);
            long expected = TEST_OFFSET + TEST_STEP;
            long actual = distributor.nextMaxId();
            assertThat(actual, equalTo(expected));
            long actual2 = distributor.nextMaxId();
            assertThat(actual2, greaterThan(actual));
        }).subscribeOn(Schedulers.parallel());
        StepVerifier.create(mono).verifyComplete();
    }
    
    @Test
    public void batchNextMaxId() {
        String namespace = MockIdGenerator.INSTANCE.generateAsString();
        IdSegmentDistributorDefinition definition = new IdSegmentDistributorDefinition(namespace, "batchNextMaxId", 1, 1);
        IdSegmentDistributor distributor = factory().create(definition);
        var segmentChainId = new SegmentChainId(distributor);
        for (int i = 0; i < 1000; i++) {
            segmentChainId.generateAsString();
        }
        var mono = Mono.fromRunnable(() -> {
            for (int i = 0; i < 1000; i++) {
                segmentChainId.generateAsString();
            }
        }).subscribeOn(Schedulers.single());
        StepVerifier.create(mono).verifyComplete();
    }
}