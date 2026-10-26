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

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.mongo.MongoMachineInitializer;
import me.ahoo.cosid.mongo.MongoMachineLeaseStore;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.MongoReactiveRemoved;
import me.ahoo.cosid.spring.boot.starter.MongoWriteConcerns;
import me.ahoo.cosid.spring.boot.starter.store.BuiltInStoreProvider;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnBuiltInMachineStore;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnMachineStoreType;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * CosId Mongo MachineIdDistributor AutoConfiguration.
 *
 * @author ahoo wang
 */
@Slf4j
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdMachineEnabled
@ConditionalOnClass(MongoMachineLeaseStore.class)
@ConditionalOnMachineStoreType("mongo")
@AutoConfigureAfter(MongoAutoConfiguration.class)
public class CosIdMongoMachineIdDistributorAutoConfiguration {
    private static final String WRITE_CONCERN = MachineProperties.PREFIX + ".distributor.mongo.write-concern";
    private final MachineProperties machineProperties;
    
    public CosIdMongoMachineIdDistributorAutoConfiguration(MachineProperties machineProperties) {
        this.machineProperties = machineProperties;
    }
    
    @Bean
    @ConditionalOnMissingBean(MachineIdDistributor.class)
    @ConditionalOnBuiltInMachineStore
    public LeaseStoreMachineIdDistributor mongoMachineIdDistributor(MongoMachineLeaseStore mongoMachineLeaseStore, MachineStateStorage localMachineState,
                                                                    ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        return new LeaseStoreMachineIdDistributor(mongoMachineLeaseStore, localMachineState, clockBackwardsSynchronizer);
    }

    /**
     * The MongoDB backend for {@link me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider} selection.
     */
    @Bean
    public BuiltInStoreProvider cosIdMongoMachineStoreProvider(ObjectProvider<MongoMachineLeaseStore> mongoMachineLeaseStore) {
        return BuiltInStoreProvider.machine("mongo", context -> mongoMachineIdDistributor(mongoMachineLeaseStore.getObject(), context.getMachineStateStorage(),
            context.getClockBackwardsSynchronizer()));
    }
    
    @Configuration
    @ConditionalOnClass(MongoClient.class)
    class Sync {
        /**
         * The configured write concern, if any, and the driver's client-side operation timeout on every CosId operation.
         */
        private MongoDatabase withTimeout(MongoDatabase mongoDatabase) {
            mongoDatabase = withWriteConcern(mongoDatabase);
            java.time.Duration timeout = machineProperties.getDistributor().getMongo().getTimeout();
            if (timeout == null || timeout.isZero()) {
                return mongoDatabase;
            }
            return mongoDatabase.withTimeout(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        
        private MongoDatabase withWriteConcern(MongoDatabase mongoDatabase) {
            return MongoWriteConcerns.apply(mongoDatabase, machineProperties.getDistributor().getMongo().getWriteConcern(), WRITE_CONCERN);
        }
        
        @Bean
        @Primary
        @ConditionalOnMissingBean
        public MongoMachineInitializer mongoMachineInitializer(MongoClient mongoClient) {
            MongoDatabase mongoDatabase = mongoClient.getDatabase(
                machineProperties.getDistributor().getMongo().getDatabase()
            );
            mongoDatabase = withTimeout(mongoDatabase);
            return new MongoMachineInitializer(mongoDatabase);
        }
        
        /**
         * The machine id lease store on MongoDB; every operation is bounded by
         * {@code cosid.machine.distributor.mongo.timeout}, and writes with {@code write-concern}.
         */
        @Bean
        @ConditionalOnMissingBean
        public MongoMachineLeaseStore mongoMachineLeaseStore(MongoClient mongoClient, ObjectProvider<MongoMachineInitializer> mongoMachineInitializer) {
            MachineProperties.Mongo mongo = machineProperties.getDistributor().getMongo();
            // The collection and its indexes, before the first claim; the store still works without the indexes.
            mongoMachineInitializer.ifAvailable(initializer -> {
                try {
                    initializer.ensureMachineCollection();
                } catch (RuntimeException ensureFailure) {
                    if (log.isWarnEnabled()) {
                        log.warn("Ensure the machine collection failed: the lease queries run without their indexes.", ensureFailure);
                    }
                }
            });
            MongoDatabase database = withWriteConcern(mongoClient.getDatabase(mongo.getDatabase()));
            MongoWriteConcerns.warnIfWeakerThanMajority(database, WRITE_CONCERN);
            return new MongoMachineLeaseStore(database,
                MachineLeaseStores.positive(mongo.getTimeout(), MachineProperties.PREFIX + ".distributor.mongo.timeout"));
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
        public MongoMachineLeaseStore mongoMachineLeaseStore() {
            return MongoReactiveRemoved.fail();
        }
    }
}
