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
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * CosId Redis Segment AutoConfiguration.
 *
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSegmentEnabled
@EnableConfigurationProperties(SegmentIdProperties.class)
@ConditionalOnClass(MongoIdSegmentDistributorFactory.class)
@ConditionalOnProperty(value = SegmentIdProperties.Distributor.TYPE, matchIfMissing = true, havingValue = "mongo")
public class CosIdMongoSegmentAutoConfiguration {
    
    private final SegmentIdProperties segmentIdProperties;
    
    public CosIdMongoSegmentAutoConfiguration(SegmentIdProperties segmentIdProperties) {
        this.segmentIdProperties = segmentIdProperties;
    }
    
    @Bean
    @Primary
    @ConditionalOnMissingBean
    @ConditionalOnBean(MongoClient.class)
    public IdSegmentInitializer idSegmentInitializer(MongoClient mongoClient) {
        MongoDatabase mongoDatabase = mongoClient.getDatabase(
            segmentIdProperties.getDistributor().getMongo().getDatabase()
        );
        return new MongoIdSegmentInitializer(mongoDatabase);
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(com.mongodb.reactivestreams.client.MongoClient.class)
    public IdSegmentInitializer reactiveIdSegmentInitializer(com.mongodb.reactivestreams.client.MongoClient mongoClient) {
        com.mongodb.reactivestreams.client.MongoDatabase mongoDatabase = mongoClient.getDatabase(
            segmentIdProperties.getDistributor().getMongo().getDatabase()
        );
        return new MongoReactiveIdSegmentInitializer(mongoDatabase);
    }
    
    @Bean
    @Primary
    @ConditionalOnMissingBean
    @ConditionalOnBean(MongoClient.class)
    public IdSegmentDistributorFactory idSegmentDistributorFactory(MongoClient mongoClient, IdSegmentInitializer idSegmentInitializer) {
        MongoDatabase mongoDatabase = mongoClient.getDatabase(
            segmentIdProperties.getDistributor().getMongo().getDatabase()
        );
        idSegmentInitializer.ensureCosIdCollection();
        return new MongoIdSegmentDistributorFactory(mongoDatabase, true);
    }
    
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(com.mongodb.reactivestreams.client.MongoClient.class)
    public IdSegmentDistributorFactory reacticeIdSegmentDistributorFactory(com.mongodb.reactivestreams.client.MongoClient mongoClient, IdSegmentInitializer idSegmentInitializer) {
        idSegmentInitializer.ensureCosIdCollection();
        
        com.mongodb.reactivestreams.client.MongoDatabase mongoDatabase = mongoClient.getDatabase(
            segmentIdProperties.getDistributor().getMongo().getDatabase()
        );
        return new MongoReactiveIdSegmentDistributorFactory(
            mongoDatabase,
            true);
    }
}
