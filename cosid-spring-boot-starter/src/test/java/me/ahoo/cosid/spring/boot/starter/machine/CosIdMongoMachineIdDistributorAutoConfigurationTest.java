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

package me.ahoo.cosid.spring.boot.starter.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.mongo.MongoMachineCollection;
import me.ahoo.cosid.mongo.MongoMachineIdDistributor;
import me.ahoo.cosid.mongo.MongoMachineInitializer;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CosIdMongoMachineIdDistributorAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdMongoMachineIdDistributorAutoConfiguration.class))
        .withClassLoader(new FilteredClassLoader(com.mongodb.reactivestreams.client.MongoClient.class))
        .withBean(MachineProperties.class, MachineProperties::new)
        .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
        .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
        .withBean(MongoClient.class, CosIdMongoMachineIdDistributorAutoConfigurationTest::mongoClient);

    @Test
    void createsSyncMongoMachineBeansWithoutConnectingToMongo() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context)
                .hasSingleBean(CosIdMongoMachineIdDistributorAutoConfiguration.class)
                .hasSingleBean(MongoMachineInitializer.class)
                .hasSingleBean(MongoMachineCollection.class)
                .hasSingleBean(MongoMachineIdDistributor.class));
    }

    @Test
    void backsOffWhenUserProvidesMongoMachineDistributor() {
        MongoMachineIdDistributor distributor = mock(MongoMachineIdDistributor.class);

        this.contextRunner
            .withBean(MongoMachineIdDistributor.class, () -> distributor)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context.getBean(MongoMachineIdDistributor.class)).isSameAs(distributor));
    }

    @Test
    void doesNotCreateMongoMachineBeansWhenMachineIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdMongoMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(MongoMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateMongoMachineBeansWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdMongoMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(MongoMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateMongoMachineBeansWhenMongoDistributorClassIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(MongoMachineIdDistributor.class))
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdMongoMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(MongoMachineIdDistributor.class));
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
