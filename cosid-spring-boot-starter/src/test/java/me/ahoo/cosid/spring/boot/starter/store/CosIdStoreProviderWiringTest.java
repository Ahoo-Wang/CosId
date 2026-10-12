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

package me.ahoo.cosid.spring.boot.starter.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributorFactory;
import me.ahoo.cosid.jdbc.JdbcMachineLeaseStore;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.HostAddressSupplier;
import me.ahoo.cosid.machine.MachineId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.ManualMachineIdDistributor;
import me.ahoo.cosid.machine.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.mongo.IdSegmentInitializer;
import me.ahoo.cosid.mongo.MongoIdSegmentDistributorFactory;
import me.ahoo.cosid.proxy.ProxyIdSegmentDistributorFactory;
import me.ahoo.cosid.proxy.api.MachineLeaseClient;
import me.ahoo.cosid.proxy.api.SegmentClient;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.spring.boot.starter.CosIdAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.ConditionalOnCosIdMachineEnabled;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdJdbcMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdMachineAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdMongoMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdProxyMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdSpringRedisMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.CosIdZookeeperMachineIdDistributorAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;
import me.ahoo.cosid.spring.boot.starter.segment.ConditionalOnCosIdSegmentEnabled;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdJdbcSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdMongoSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdProxySegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdSpringRedisSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.CosIdZookeeperSegmentAutoConfiguration;
import me.ahoo.cosid.spring.boot.starter.segment.SegmentIdProperties;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributorFactory;
import me.ahoo.cosid.zookeeper.ZookeeperIdSegmentDistributorFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.function.Function;
import javax.sql.DataSource;

/**
 * Each backend registers its own provider, which creates the same distributor or factory as the backend's own bean.
 */
class CosIdStoreProviderWiringTest {
    private static final String PROXY_HOST = "cosid.proxy.host=http://localhost:8688";
    private final ApplicationContextRunner machineRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdMachineStoreAutoConfiguration.class))
        .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
        .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
        .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true", PROXY_HOST,
            MachineProperties.PREFIX + ".distributor.jdbc.enable-auto-init-cosid-machine-table=false",
            MachineProperties.PREFIX + ".distributor.manual.machine-id=1");
    
    private final ApplicationContextRunner segmentRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CosIdSegmentStoreAutoConfiguration.class))
        .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", PROXY_HOST,
            SegmentIdProperties.PREFIX + ".distributor.jdbc.enable-auto-init-cosid-table=false");
    
    private static CosIdStoreProvider provider(AssertableApplicationContext context, String type) {
        assertThat(context).hasNotFailed();
        return context.getBeansOfType(CosIdStoreProvider.class).values().stream()
            .filter(provider -> provider.getType().equals(type))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No provider of type " + type));
    }
    
    private void assertMachine(ApplicationContextRunner runner, Class<?> configuration, String type, Class<? extends MachineIdDistributor> expected) {
        runner.withConfiguration(AutoConfigurations.of(configuration))
            .withPropertyValues(MachineProperties.Distributor.TYPE + "=" + type)
            .run(context -> {
                assertThat(context.getBean(MachineIdDistributor.class)).isInstanceOf(expected);
                MachineStoreContext storeContext = new MachineStoreContext(context.getBean(MachineProperties.class), MachineStateStorage.IN_MEMORY,
                    ClockBackwardsSynchronizer.DEFAULT);
                assertThat(provider(context, type).createMachineIdDistributor(storeContext)).isInstanceOf(expected);
                assertThat(provider(context, type).createIdSegmentDistributorFactory(new SegmentStoreContext(new SegmentIdProperties()))).isNull();
            });
    }
    
    private void assertSegment(ApplicationContextRunner runner, Class<?> configuration, String type, Class<? extends IdSegmentDistributorFactory> expected) {
        runner.withConfiguration(AutoConfigurations.of(configuration))
            .withPropertyValues(SegmentIdProperties.Distributor.TYPE + "=" + type)
            .run(context -> {
                assertThat(context.getBean(IdSegmentDistributorFactory.class)).isInstanceOf(expected);
                assertThat(provider(context, type).createIdSegmentDistributorFactory(new SegmentStoreContext(context.getBean(SegmentIdProperties.class))))
                    .isInstanceOf(expected);
            });
    }
    
    @Test
    void manualAndStatefulSet() {
        Function<ApplicationContextRunner, ApplicationContextRunner> host = runner -> runner
            .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class))
            .withBean(HostAddressSupplier.class, () -> () -> "10.0.0.10")
            .withBean(MachineId.class, () -> new MachineId(1));
        assertMachine(host.apply(machineRunner), CosIdMachineAutoConfiguration.class, "manual", ManualMachineIdDistributor.class);
        assertMachine(host.apply(machineRunner), CosIdMachineAutoConfiguration.class, "stateful_set", StatefulSetMachineIdDistributor.class);
    }
    
    @Test
    void jdbc() {
        assertMachine(machineRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(JdbcMachineLeaseStore.class, () -> mock(JdbcMachineLeaseStore.class)),
            CosIdJdbcMachineIdDistributorAutoConfiguration.class, "jdbc", LeaseStoreMachineIdDistributor.class);
        assertSegment(segmentRunner.withBean(DataSource.class, () -> mock(DataSource.class)),
            CosIdJdbcSegmentAutoConfiguration.class, "jdbc", JdbcIdSegmentDistributorFactory.class);
    }
    
    @Test
    void redis() {
        assertMachine(machineRunner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class)),
            CosIdSpringRedisMachineIdDistributorAutoConfiguration.class, "redis", LeaseStoreMachineIdDistributor.class);
        assertSegment(segmentRunner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class)),
            CosIdSpringRedisSegmentAutoConfiguration.class, "redis", SpringRedisIdSegmentDistributorFactory.class);
    }
    
    @Test
    void zookeeper() {
        Function<ApplicationContextRunner, ApplicationContextRunner> curator = runner -> runner
            .withBean(CuratorFramework.class, () -> mock(CuratorFramework.class))
            .withBean(RetryPolicy.class, () -> mock(RetryPolicy.class));
        assertMachine(curator.apply(machineRunner), CosIdZookeeperMachineIdDistributorAutoConfiguration.class, "zookeeper",
            LeaseStoreMachineIdDistributor.class);
        assertSegment(curator.apply(segmentRunner), CosIdZookeeperSegmentAutoConfiguration.class, "zookeeper",
            ZookeeperIdSegmentDistributorFactory.class);
    }
    
    @Test
    void mongo() {
        Function<ApplicationContextRunner, ApplicationContextRunner> mongo = runner -> runner
            .withClassLoader(new FilteredClassLoader(com.mongodb.reactivestreams.client.MongoClient.class))
            .withBean(MongoClient.class, CosIdStoreProviderWiringTest::mongoClient);
        assertMachine(mongo.apply(machineRunner), CosIdMongoMachineIdDistributorAutoConfiguration.class, "mongo", LeaseStoreMachineIdDistributor.class);
        assertSegment(mongo.apply(segmentRunner).withBean(IdSegmentInitializer.class, () -> mock(IdSegmentInitializer.class)),
            CosIdMongoSegmentAutoConfiguration.class, "mongo", MongoIdSegmentDistributorFactory.class);
    }
    
    @Test
    void proxy() {
        assertMachine(machineRunner.withBean(MachineLeaseClient.class, () -> mock(MachineLeaseClient.class)),
            CosIdProxyMachineIdDistributorAutoConfiguration.class, "proxy", LeaseStoreMachineIdDistributor.class);
        assertSegment(segmentRunner.withBean(SegmentClient.class, () -> mock(SegmentClient.class)),
            CosIdProxySegmentAutoConfiguration.class, "proxy", ProxyIdSegmentDistributorFactory.class);
    }
    
    @SuppressWarnings("unchecked")
    private static MongoClient mongoClient() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase(anyString())).thenReturn(database);
        when(database.getCollection(anyString())).thenReturn(collection);
        when(database.withTimeout(anyLong(), any())).thenReturn(database);
        return client;
    }
}
