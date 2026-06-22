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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.ahoo.cosid.mongo.MongoIdSegmentDistributorFactory;
import me.ahoo.cosid.mongo.MongoIdSegmentInitializer;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdMongoSegmentAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdMongoSegmentAutoConfiguration.class))
        .withClassLoader(new FilteredClassLoader(com.mongodb.reactivestreams.client.MongoClient.class))
        .withBean(MongoClient.class, CosIdMongoSegmentAutoConfigurationTest::mongoClient);

    @Test
    void createsSyncMongoSegmentBeansWithoutConnectingToMongo() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context)
                .hasSingleBean(CosIdMongoSegmentAutoConfiguration.class)
                .hasSingleBean(SegmentIdProperties.class)
                .hasSingleBean(MongoIdSegmentInitializer.class)
                .hasSingleBean(MongoIdSegmentDistributorFactory.class));
    }

    @Test
    void backsOffWhenUserProvidesMongoSegmentFactory() {
        MongoIdSegmentDistributorFactory factory = mock(MongoIdSegmentDistributorFactory.class);

        this.contextRunner
            .withBean(MongoIdSegmentDistributorFactory.class, () -> factory)
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context.getBean(MongoIdSegmentDistributorFactory.class)).isSameAs(factory));
    }

    @Test
    void doesNotCreateMongoSegmentBeansWhenSegmentIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdMongoSegmentAutoConfiguration.class)
                .doesNotHaveBean(MongoIdSegmentDistributorFactory.class));
    }

    @Test
    void doesNotCreateMongoSegmentBeansWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdMongoSegmentAutoConfiguration.class)
                .doesNotHaveBean(MongoIdSegmentDistributorFactory.class));
    }

    @Test
    void doesNotCreateMongoSegmentBeansWhenMongoFactoryClassIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(MongoIdSegmentDistributorFactory.class))
            .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdMongoSegmentAutoConfiguration.class)
                .doesNotHaveBean(MongoIdSegmentDistributorFactory.class));
    }

    private static MongoClient mongoClient() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase(anyString())).thenReturn(database);
        when(database.getCollection(anyString())).thenReturn(collection);
        return client;
    }
}
