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
import me.ahoo.cosid.mongo.reactive.MongoReactiveIdSegmentDistributorFactory;
import me.ahoo.cosid.mongo.reactive.MongoReactiveIdSegmentInitializer;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@AutoConfigureAfter(value = {MongoAutoConfiguration.class, MongoReactiveAutoConfiguration.class})
public class CosIdMongoSegmentAutoConfiguration {
    
    private final SegmentIdProperties segmentIdProperties;
    
    public CosIdMongoSegmentAutoConfiguration(SegmentIdProperties segmentIdProperties) {
        this.segmentIdProperties = segmentIdProperties;
    }
    
    @Order(0)
    @Configuration
    @ConditionalOnClass(MongoClient.class)
    class Sync {
        @Bean
        @Primary
        @ConditionalOnMissingBean
        public MongoIdSegmentInitializer mongoIdSegmentInitializer(MongoClient mongoClient) {
            MongoDatabase mongoDatabase = mongoClient.getDatabase(
                segmentIdProperties.getDistributor().getMongo().getDatabase()
            );
            return new MongoIdSegmentInitializer(mongoDatabase);
        }
        
        @Bean
        @Primary
        @ConditionalOnMissingBean
        public MongoIdSegmentDistributorFactory mongoIdSegmentDistributorFactory(MongoClient mongoClient, IdSegmentInitializer idSegmentInitializer) {
            MongoDatabase mongoDatabase = mongoClient.getDatabase(
                segmentIdProperties.getDistributor().getMongo().getDatabase()
            );
            idSegmentInitializer.ensureCosIdCollection();
            return new MongoIdSegmentDistributorFactory(mongoDatabase, true);
        }
    }
    
    @Configuration
    @ConditionalOnClass(com.mongodb.reactivestreams.client.MongoClient.class)
    class Reactive {
        @Bean
        @ConditionalOnMissingBean
        public MongoReactiveIdSegmentInitializer mongoReactiveIdSegmentInitializer(com.mongodb.reactivestreams.client.MongoClient mongoClient) {
            com.mongodb.reactivestreams.client.MongoDatabase mongoDatabase = mongoClient.getDatabase(
                segmentIdProperties.getDistributor().getMongo().getDatabase()
            );
            return new MongoReactiveIdSegmentInitializer(mongoDatabase);
        }
        
        @Bean
        @ConditionalOnMissingBean
        public MongoReactiveIdSegmentDistributorFactory mongoReactiveIdSegmentDistributorFactory(com.mongodb.reactivestreams.client.MongoClient mongoClient,
                                                                                                 IdSegmentInitializer idSegmentInitializer) {
            idSegmentInitializer.ensureCosIdCollection();
            
            com.mongodb.reactivestreams.client.MongoDatabase mongoDatabase = mongoClient.getDatabase(
                segmentIdProperties.getDistributor().getMongo().getDatabase()
            );
            return new MongoReactiveIdSegmentDistributorFactory(
                mongoDatabase,
                true);
        }
    }
}
