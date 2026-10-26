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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.mongo.MongoMachineInitializer;
import me.ahoo.cosid.mongo.MongoMachineLeaseStore;
import me.ahoo.cosid.spring.boot.starter.MongoReactiveRemoved;

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
                .hasSingleBean(MongoMachineLeaseStore.class)
                .hasSingleBean(LeaseStoreMachineIdDistributor.class));
    }

    @Test
    void boundsMongoOperationsWithTimeout() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> {
                assertThat(context).hasSingleBean(MongoMachineLeaseStore.class);
                MongoDatabase database = context.getBean(MongoClient.class).getDatabase("cosid_db");
                verify(database, org.mockito.Mockito.atLeastOnce()).withTimeout(5000L, java.util.concurrent.TimeUnit.MILLISECONDS);
            });
    }

    @Test
    void zeroTimeoutFailsStartupNamingTheProperty() {
        MongoClient client = mongoClient();
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdMongoMachineIdDistributorAutoConfiguration.class))
            .withClassLoader(new FilteredClassLoader(com.mongodb.reactivestreams.client.MongoClient.class))
            .withBean(MachineProperties.class, () -> {
                MachineProperties machineProperties = new MachineProperties();
                machineProperties.getDistributor().getMongo().setTimeout(java.time.Duration.ZERO);
                return machineProperties;
            })
            .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
            .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
            .withBean(MongoClient.class, () -> client)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessageContaining("cosid.machine.distributor.mongo.timeout"));
    }

    @Test
    void backsOffWhenUserProvidesMongoMachineDistributor() {
        MachineIdDistributor distributor = mock(MachineIdDistributor.class);

        this.contextRunner
            .withBean(MachineIdDistributor.class, () -> distributor)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context.getBean(MachineIdDistributor.class)).isSameAs(distributor));
    }

    @Test
    void doesNotCreateMongoMachineBeansWhenMachineIsDisabled() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=false")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdMongoMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(LeaseStoreMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateMongoMachineBeansWhenTypeDoesNotMatch() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=redis")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdMongoMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(LeaseStoreMachineIdDistributor.class));
    }

    @Test
    void doesNotCreateMongoMachineBeansWhenMongoDistributorClassIsMissing() {
        this.contextRunner
            .withClassLoader(new FilteredClassLoader(MongoMachineLeaseStore.class))
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true")
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context)
                .doesNotHaveBean(CosIdMongoMachineIdDistributorAutoConfiguration.class)
                .doesNotHaveBean(LeaseStoreMachineIdDistributor.class));
    }

    private static MongoClient mongoClient() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase(anyString())).thenReturn(database);
        when(database.getCollection(anyString())).thenReturn(collection);
        when(database.withTimeout(anyLong(), any())).thenReturn(database);
        when(database.withWriteConcern(any())).thenReturn(database);
        return client;
    }

    @Test
    void theMachineCollectionAndItsIndexesAreEnsuredBeforeTheLeaseStore() {
        MongoMachineInitializer initializer = mock(MongoMachineInitializer.class);
        this.contextRunner
            .withBean(MongoMachineInitializer.class, () -> initializer)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true", MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(MongoMachineLeaseStore.class);
                verify(initializer).ensureMachineCollection();
            });
    }
    
    @Test
    void aFailedEnsureOnlyWarns() {
        MongoMachineInitializer initializer = mock(MongoMachineInitializer.class);
        when(initializer.ensureMachineCollection()).thenThrow(new IllegalStateException("not authorized to create indexes"));
        this.contextRunner
            .withBean(MongoMachineInitializer.class, () -> initializer)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true", MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context).hasNotFailed().hasSingleBean(MongoMachineLeaseStore.class));
    }
    
    @Test
    void failsAtStartupWithOnlyTheReactiveDriver() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdMongoMachineIdDistributorAutoConfiguration.class))
            .withClassLoader(new FilteredClassLoader(MongoClient.class))
            .withBean(MachineProperties.class, MachineProperties::new)
            .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
            .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true", MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessage(MongoReactiveRemoved.MESSAGE));
    }
    
    @Test
    void writesWithTheConfiguredWriteConcern() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        MongoDatabase majority = mock(MongoDatabase.class);
        MongoDatabase majorityWithTimeout = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        MongoCollection<Document> majorityCollection = mock(MongoCollection.class);
        when(client.getDatabase(anyString())).thenReturn(database);
        when(database.getCollection(anyString())).thenReturn(collection);
        when(database.withTimeout(anyLong(), any())).thenReturn(database);
        when(database.withWriteConcern(com.mongodb.WriteConcern.MAJORITY)).thenReturn(majority);
        when(majority.withTimeout(anyLong(), any())).thenReturn(majorityWithTimeout);
        when(majorityWithTimeout.getCollection(anyString())).thenReturn(majorityCollection);
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CosIdMongoMachineIdDistributorAutoConfiguration.class))
            .withClassLoader(new FilteredClassLoader(com.mongodb.reactivestreams.client.MongoClient.class))
            .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
            .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
            .withBean(MongoClient.class, () -> client)
            .withBean(MachineProperties.class, () -> {
                MachineProperties machineProperties = new MachineProperties();
                machineProperties.getDistributor().getMongo().setWriteConcern("majority");
                return machineProperties;
            })
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true", MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(MongoMachineLeaseStore.class);
                // The lease store writes to the collection of the database with the majority write concern, and the timeout.
                assertThat(context.getBean(MongoMachineLeaseStore.class)).extracting("collection").isSameAs(majorityCollection);
                verify(database, never()).getCollection(me.ahoo.cosid.mongo.MachineOperates.COLLECTION_NAME);
            });
    }

    @Test
    void keepsTheConnectionStringsWriteConcernByDefault() {
        this.contextRunner
            .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true", MachineProperties.Distributor.TYPE + "=mongo")
            .run(context -> {
                assertThat(context).hasSingleBean(MongoMachineLeaseStore.class);
                MongoDatabase database = context.getBean(MongoClient.class).getDatabase("cosid_db");
                verify(database, never()).withWriteConcern(any());
            });
    }
}
