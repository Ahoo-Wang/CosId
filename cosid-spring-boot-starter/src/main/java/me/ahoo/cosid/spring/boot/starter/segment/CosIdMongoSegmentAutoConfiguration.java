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

package me.ahoo.cosid.spring.boot.starter.segment;

import me.ahoo.cosid.mongo.IdSegmentInitializer;
import me.ahoo.cosid.mongo.MongoIdSegmentDistributorFactory;
import me.ahoo.cosid.mongo.MongoIdSegmentInitializer;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.MongoReactiveRemoved;
import me.ahoo.cosid.spring.boot.starter.store.BuiltInStoreProvider;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnBuiltInSegmentStore;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;

/**
 * CosId Redis Segment AutoConfiguration.
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSegmentEnabled
@EnableConfigurationProperties(SegmentIdProperties.class)
@ConditionalOnClass(MongoIdSegmentDistributorFactory.class)
@ConditionalOnProperty(value = SegmentIdProperties.Distributor.TYPE, havingValue = "mongo")
@AutoConfigureAfter(MongoAutoConfiguration.class)
public class CosIdMongoSegmentAutoConfiguration {
    
    private final SegmentIdProperties segmentIdProperties;
    
    public CosIdMongoSegmentAutoConfiguration(SegmentIdProperties segmentIdProperties) {
        this.segmentIdProperties = segmentIdProperties;
    }
    
    @Order(0)
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MongoClient.class)
    class Sync {
        /**
         * Bounds every CosId operation with the driver's client-side operation timeout.
         */
        private MongoDatabase withTimeout(MongoDatabase mongoDatabase) {
            java.time.Duration timeout = segmentIdProperties.getDistributor().getMongo().getTimeout();
            if (timeout == null || timeout.isZero()) {
                return mongoDatabase;
            }
            return mongoDatabase.withTimeout(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        
        @Bean
        @Primary
        @ConditionalOnMissingBean
        public MongoIdSegmentInitializer mongoIdSegmentInitializer(MongoClient mongoClient) {
            MongoDatabase mongoDatabase = mongoClient.getDatabase(
                segmentIdProperties.getDistributor().getMongo().getDatabase()
            );
            mongoDatabase = withTimeout(mongoDatabase);
            return new MongoIdSegmentInitializer(mongoDatabase);
        }
        
        @Bean
        @Primary
        @ConditionalOnMissingBean
        @ConditionalOnBuiltInSegmentStore
        public MongoIdSegmentDistributorFactory mongoIdSegmentDistributorFactory(MongoClient mongoClient, IdSegmentInitializer idSegmentInitializer) {
            MongoDatabase mongoDatabase = mongoClient.getDatabase(
                segmentIdProperties.getDistributor().getMongo().getDatabase()
            );
            mongoDatabase = withTimeout(mongoDatabase);
            idSegmentInitializer.ensureCosIdCollection();
            return new MongoIdSegmentDistributorFactory(mongoDatabase);
        }

        /**
         * The MongoDB backend for {@link me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider} selection selection.
         */
        @Bean
        public BuiltInStoreProvider cosIdMongoSegmentStoreProvider(ObjectProvider<MongoClient> mongoClient, ObjectProvider<IdSegmentInitializer> idSegmentInitializer) {
            return BuiltInStoreProvider.segment("mongo", context -> mongoIdSegmentDistributorFactory(mongoClient.getObject(), idSegmentInitializer.getObject()));
        }
    }
    
    /**
     * Only the reactive driver: MongoDB reactive support was removed in 4.0, so startup fails with a hint.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.mongodb.reactivestreams.client.MongoClient")
    @ConditionalOnMissingClass("com.mongodb.client.MongoClient")
    static class ReactiveOnly {
        @Bean
        public IdSegmentInitializer mongoIdSegmentInitializer() {
            return MongoReactiveRemoved.fail();
        }
    }
}
