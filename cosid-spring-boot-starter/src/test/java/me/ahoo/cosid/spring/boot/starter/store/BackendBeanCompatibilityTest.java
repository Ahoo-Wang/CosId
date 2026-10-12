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

import me.ahoo.cosid.jdbc.JdbcMachineLeaseStore;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.HostAddressSupplier;
import me.ahoo.cosid.machine.MachineId;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.ManualMachineIdDistributor;
import me.ahoo.cosid.machine.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.mongo.IdSegmentInitializer;
import me.ahoo.cosid.mongo.MongoIdSegmentDistributorFactory;
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

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;

/**
 * The backend beans keep their 3.x names and concrete types, with the store auto-configurations present: applications
 * inject them by concrete type and refer to them by name.
 */
class BackendBeanCompatibilityTest {
    private static final String PROXY_HOST = "cosid.proxy.host=http://localhost:8688";
    
    private final ApplicationContextRunner machineRunner = new ApplicationContextRunner()
        .withBean(MachineStateStorage.class, () -> MachineStateStorage.IN_MEMORY)
        .withBean(ClockBackwardsSynchronizer.class, () -> ClockBackwardsSynchronizer.DEFAULT)
        .withPropertyValues(ConditionalOnCosIdMachineEnabled.ENABLED_KEY + "=true", PROXY_HOST,
            MachineProperties.PREFIX + ".distributor.jdbc.enable-auto-init-cosid-machine-table=false",
            MachineProperties.PREFIX + ".distributor.manual.machine-id=1");
    
    private final ApplicationContextRunner segmentRunner = new ApplicationContextRunner()
        .withPropertyValues(ConditionalOnCosIdSegmentEnabled.ENABLED_KEY + "=true", PROXY_HOST);
    
    private void assertStarts(ApplicationContextRunner runner, Class<?> backend, Class<?> store, String type, String typeKey, Class<?> probe) {
        runner.withConfiguration(AutoConfigurations.of(backend, store))
            .withUserConfiguration(probe)
            .withPropertyValues(typeKey + "=" + type)
            .run(context -> assertThat(context).hasNotFailed().hasBean("probe"));
    }
    
    private void assertMachine(ApplicationContextRunner runner, Class<?> backend, String type, Class<?> probe) {
        assertStarts(runner, backend, CosIdMachineStoreAutoConfiguration.class, type, MachineProperties.Distributor.TYPE, probe);
    }
    
    private void assertSegment(ApplicationContextRunner runner, Class<?> backend, String type, Class<?> probe) {
        assertStarts(runner, backend, CosIdSegmentStoreAutoConfiguration.class, type, SegmentIdProperties.Distributor.TYPE, probe);
    }
    
    @Test
    void manualAndStatefulSet() {
        ApplicationContextRunner runner = machineRunner
            .withConfiguration(AutoConfigurations.of(CosIdAutoConfiguration.class))
            .withBean(HostAddressSupplier.class, () -> () -> "10.0.0.10")
            .withBean(MachineId.class, () -> new MachineId(1));
        assertMachine(runner, CosIdMachineAutoConfiguration.class, "manual", ManualProbe.class);
        assertMachine(runner, CosIdMachineAutoConfiguration.class, "stateful_set", StatefulSetProbe.class);
    }
    
    @Test
    void jdbc() {
        assertMachine(machineRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(JdbcMachineLeaseStore.class, () -> mock(JdbcMachineLeaseStore.class)),
            CosIdJdbcMachineIdDistributorAutoConfiguration.class, "jdbc", JdbcMachineProbe.class);
        assertSegment(segmentRunner.withBean(DataSource.class, () -> mock(DataSource.class)),
            CosIdJdbcSegmentAutoConfiguration.class, "jdbc", SegmentFactoryProbe.class);
    }
    
    @Test
    void redis() {
        assertMachine(machineRunner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class)),
            CosIdSpringRedisMachineIdDistributorAutoConfiguration.class, "redis", RedisMachineProbe.class);
        assertSegment(segmentRunner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class)),
            CosIdSpringRedisSegmentAutoConfiguration.class, "redis", SegmentFactoryProbe.class);
    }
    
    @Test
    void zookeeper() {
        assertMachine(machineRunner.withBean(CuratorFramework.class, () -> mock(CuratorFramework.class)).withBean(RetryPolicy.class, () -> mock(RetryPolicy.class)),
            CosIdZookeeperMachineIdDistributorAutoConfiguration.class, "zookeeper", ZookeeperMachineProbe.class);
        assertSegment(segmentRunner.withBean(CuratorFramework.class, () -> mock(CuratorFramework.class)).withBean(RetryPolicy.class, () -> mock(RetryPolicy.class)),
            CosIdZookeeperSegmentAutoConfiguration.class, "zookeeper", SegmentFactoryProbe.class);
    }
    
    @Test
    void mongo() {
        assertMachine(machineRunner.withClassLoader(new FilteredClassLoader(com.mongodb.reactivestreams.client.MongoClient.class))
                .withBean(MongoClient.class, BackendBeanCompatibilityTest::mongoClient),
            CosIdMongoMachineIdDistributorAutoConfiguration.class, "mongo", MongoMachineProbe.class);
        assertSegment(segmentRunner.withClassLoader(new FilteredClassLoader(com.mongodb.reactivestreams.client.MongoClient.class))
                .withBean(MongoClient.class, BackendBeanCompatibilityTest::mongoClient)
                .withBean(IdSegmentInitializer.class, () -> mock(IdSegmentInitializer.class)),
            CosIdMongoSegmentAutoConfiguration.class, "mongo", MongoSegmentProbe.class);
    }
    
    @Test
    void proxy() {
        assertMachine(machineRunner.withBean(MachineLeaseClient.class, () -> mock(MachineLeaseClient.class)),
            CosIdProxyMachineIdDistributorAutoConfiguration.class, "proxy", ProxyMachineProbe.class);
        assertSegment(segmentRunner.withBean(SegmentClient.class, () -> mock(SegmentClient.class)),
            CosIdProxySegmentAutoConfiguration.class, "proxy", SegmentFactoryProbe.class);
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
    
    @Configuration(proxyBeanMethods = false)
    static class ManualProbe {
        @Bean
        String probe(@Qualifier("machineIdDistributor") ManualMachineIdDistributor distributor) {
            return distributor.toString();
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    static class StatefulSetProbe {
        @Bean
        String probe(@Qualifier("statefulSetMachineIdDistributor") StatefulSetMachineIdDistributor distributor) {
            return distributor.toString();
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    static class JdbcMachineProbe {
        @Bean
        String probe(@Qualifier("jdbcMachineIdDistributor") LeaseStoreMachineIdDistributor distributor) {
            return distributor.toString();
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    static class RedisMachineProbe {
        @Bean
        String probe(@Qualifier("springRedisMachineIdDistributor") LeaseStoreMachineIdDistributor distributor) {
            return distributor.toString();
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    static class ZookeeperMachineProbe {
        @Bean
        String probe(@Qualifier("zookeeperMachineIdDistributor") LeaseStoreMachineIdDistributor distributor) {
            return distributor.toString();
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    static class MongoMachineProbe {
        @Bean
        String probe(@Qualifier("mongoMachineIdDistributor") LeaseStoreMachineIdDistributor distributor) {
            return distributor.toString();
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    static class ProxyMachineProbe {
        @Bean
        String probe(@Qualifier("proxyMachineIdDistributor") LeaseStoreMachineIdDistributor distributor) {
            return distributor.toString();
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    static class SegmentFactoryProbe {
        @Bean
        String probe(@Qualifier("idSegmentDistributorFactory") IdSegmentDistributorFactory factory) {
            return factory.toString();
        }
    }
    
    @Configuration(proxyBeanMethods = false)
    static class MongoSegmentProbe {
        @Bean
        String probe(@Qualifier("mongoIdSegmentDistributorFactory") MongoIdSegmentDistributorFactory factory) {
            return factory.toString();
        }
    }
}
