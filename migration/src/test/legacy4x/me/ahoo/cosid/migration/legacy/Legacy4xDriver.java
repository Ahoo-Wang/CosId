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

package me.ahoo.cosid.migration.legacy;

import me.ahoo.cosid.jdbc.JdbcDialect;
import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributor;
import me.ahoo.cosid.jdbc.JdbcIdSegmentInitializer;
import me.ahoo.cosid.jdbc.JdbcMachineIdInitializer;
import me.ahoo.cosid.jdbc.JdbcMachineLeaseStore;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineState;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.machine.lease.MachineLeaseStore;
import me.ahoo.cosid.mongo.MongoIdSegmentDistributorFactory;
import me.ahoo.cosid.mongo.MongoMachineInitializer;
import me.ahoo.cosid.mongo.MongoMachineLeaseStore;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.IdSegmentDistributorDefinition;
import me.ahoo.cosid.segment.IdSegmentDistributorFactory;
import me.ahoo.cosid.segment.grouped.DefaultGroupedIdSegmentDistributor;
import me.ahoo.cosid.segment.grouped.date.YearMonthDayGroupBySupplier;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.spring.redis.RedisCommandTimeout;
import me.ahoo.cosid.spring.redis.SpringRedisIdSegmentDistributor;
import me.ahoo.cosid.spring.redis.SpringRedisMachineLeaseStore;
import me.ahoo.cosid.util.CallTimeout;
import me.ahoo.cosid.zookeeper.ZookeeperIdSegmentDistributor;
import me.ahoo.cosid.zookeeper.ZookeeperMachineLeaseStore;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.PrintWriter;
import java.net.URI;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Writes real 4.x data with the released 4.1.0 jars (09 §5). It lives in a class loader of its own, beside the 5.0
 * classes, and speaks to the tests in JDK types only: {@code apply(operation, arguments)}.
 *
 * <p>Arguments name the backend ({@code backend}: jdbc, redis, mongo, zookeeper) and its address ({@code url}). The
 * operations: {@code segment} (fetch {@code fetches} segments of {@code name}, return the last max id), {@code grouped}
 * (the same through a 4.x grouped distributor by day, return {@code {groupKey, maxId}}), {@code machines} (distribute
 * machine ids to {@code instances} instances, generate {@code ids} snowflake ids on each, release half of them, return
 * {@code {maxId, maxTimestamp}}), {@code leftover.start} / {@code leftover.stop} (a 4.x instance left running: it keeps
 * fetching {@code name} every {@code intervalMillis}; stop returns every max id it got), {@code close}.
 */
public final class Legacy4xDriver implements BiFunction<String, Map<String, Object>, Object> {
    private final Map<String, Backend> backends = new ConcurrentHashMap<>();
    private final Map<String, Leftover> leftovers = new ConcurrentHashMap<>();

    @Override
    public Object apply(String operation, Map<String, Object> args) {
        try {
            return switch (operation) {
                case "segment" -> segment(args);
                case "grouped" -> grouped(args);
                case "machines" -> machines(args);
                case "leftover.start" -> startLeftover(args);
                case "leftover.stop" -> leftovers.remove((String) args.get("handle")).stop();
                case "close" -> {
                    leftovers.values().forEach(Leftover::stop);
                    leftovers.clear();
                    backends.values().forEach(Backend::close);
                    backends.clear();
                    yield null;
                }
                default -> throw new IllegalArgumentException("Unknown operation " + operation);
            };
        } catch (RuntimeException e) {
            throw new IllegalStateException(operation + " " + args + ": " + e, e);
        }
    }

    private Backend backend(Map<String, Object> args) {
        String backend = (String) args.get("backend");
        String url = (String) args.get("url");
        return backends.computeIfAbsent(backend + " " + url, key -> switch (backend) {
            case "jdbc" -> new Jdbc(url);
            case "redis" -> new Redis(url);
            case "mongo" -> new Mongo(url);
            case "zookeeper" -> new Zookeeper(url);
            default -> throw new IllegalArgumentException("Unknown backend " + backend);
        });
    }

    private Object segment(Map<String, Object> args) {
        IdSegmentDistributor distributor = backend(args).distributor(new IdSegmentDistributorDefinition((String) args.get("namespace"),
            (String) args.get("name"), number(args, "offset", 0), number(args, "step", 10)));
        long maxId = -1;
        for (long i = 0; i < number(args, "fetches", 1); i++) {
            maxId = distributor.nextMaxId();
        }
        return maxId;
    }

    private Object grouped(Map<String, Object> args) {
        Backend backend = backend(args);
        DefaultGroupedIdSegmentDistributor distributor = new DefaultGroupedIdSegmentDistributor(new YearMonthDayGroupBySupplier("yyyyMMdd"),
            new IdSegmentDistributorDefinition((String) args.get("namespace"), (String) args.get("name"), 0, number(args, "step", 10)), backend::distributor);
        long maxId = -1;
        for (long i = 0; i < number(args, "fetches", 1); i++) {
            maxId = distributor.nextMaxId();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupKey", distributor.group().getKey());
        result.put("maxId", maxId);
        return result;
    }

    private Object machines(Map<String, Object> args) {
        String namespace = (String) args.get("namespace");
        int machineBit = (int) number(args, "machineBit", 10);
        MachineIdDistributor distributor = new LeaseStoreMachineIdDistributor(backend(args).leaseStore(), MachineStateStorage.IN_MEMORY,
            ClockBackwardsSynchronizer.DEFAULT);
        long maxId = 0;
        long maxTimestamp = 0;
        List<Integer> machineIds = new ArrayList<>();
        int instances = (int) number(args, "instances", 2);
        for (int i = 0; i < instances; i++) {
            InstanceId instance = InstanceId.of("legacy-" + i + "-" + UUID.randomUUID(), false);
            MachineState state = distributor.distribute(namespace, machineBit, instance, Duration.ofMinutes(5));
            machineIds.add(state.getMachineId());
            MillisecondSnowflakeId snowflake = new MillisecondSnowflakeId(1577203200000L, 41, machineBit, 12, state.getMachineId());
            for (long n = 0; n < number(args, "ids", 1000); n++) {
                maxId = Math.max(maxId, snowflake.generate());
            }
            maxTimestamp = Math.max(maxTimestamp, snowflake.getLastTimestamp());
            distributor.guard(namespace, instance, Duration.ofMinutes(5));
            if (i % 2 == 0) {
                distributor.revert(namespace, instance);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maxId", maxId);
        result.put("maxTimestamp", maxTimestamp);
        result.put("machineIds", machineIds);
        return result;
    }

    private Object startLeftover(Map<String, Object> args) {
        IdSegmentDistributor distributor = backend(args).distributor(new IdSegmentDistributorDefinition((String) args.get("namespace"),
            (String) args.get("name"), 0, number(args, "step", 10)));
        Leftover leftover = new Leftover(distributor, number(args, "intervalMillis", 100));
        String handle = UUID.randomUUID().toString();
        leftovers.put(handle, leftover);
        leftover.thread.start();
        return handle;
    }

    private static long number(Map<String, Object> args, String name, long fallback) {
        Object value = args.get(name);
        return value == null ? fallback : ((Number) value).longValue();
    }

    /** A 4.x instance nobody stopped: it keeps fetching segments. */
    private static final class Leftover {
        private final List<Long> maxIds = Collections.synchronizedList(new ArrayList<>());
        private final Thread thread;
        private volatile boolean running = true;

        Leftover(IdSegmentDistributor distributor, long intervalMillis) {
            this.thread = new Thread(() -> {
                while (running) {
                    maxIds.add(distributor.nextMaxId());
                    try {
                        Thread.sleep(intervalMillis);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }, "cosid-4x-leftover");
            thread.setDaemon(true);
        }

        List<Long> stop() {
            running = false;
            try {
                thread.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            synchronized (maxIds) {
                return new ArrayList<>(maxIds);
            }
        }
    }

    private interface Backend extends IdSegmentDistributorFactory {
        IdSegmentDistributor distributor(IdSegmentDistributorDefinition definition);

        MachineLeaseStore leaseStore();

        @Override
        default IdSegmentDistributor create(IdSegmentDistributorDefinition definition) {
            return distributor(definition);
        }

        void close();
    }

    private static final class Jdbc implements Backend {
        private final DataSource dataSource;
        private final JdbcDialect dialect;

        Jdbc(String url) {
            this.dataSource = new UrlDataSource(url);
            try {
                this.dialect = JdbcDialect.of(dataSource).orElseThrow();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
            new JdbcIdSegmentInitializer(dialect.getInitCosIdTableSql(), dialect.getInitIdSegmentSql(), dataSource).tryInitCosIdTable();
            new JdbcMachineIdInitializer(dataSource, dialect.getInitCosIdMachineTableSql(), dialect.getInitNamespaceIdxSql(), dialect.getInitInstanceIdIdxSql())
                .tryInitCosIdMachineTable();
        }

        @Override
        public IdSegmentDistributor distributor(IdSegmentDistributorDefinition definition) {
            new JdbcIdSegmentInitializer(dialect.getInitCosIdTableSql(), dialect.getInitIdSegmentSql(), dataSource)
                .tryInitIdSegment(definition.getNamespacedName(), definition.getOffset());
            return new JdbcIdSegmentDistributor(definition.getNamespace(), definition.getName(), definition.getStep(), dataSource);
        }

        @Override
        public MachineLeaseStore leaseStore() {
            return new JdbcMachineLeaseStore(dataSource);
        }

        @Override
        public void close() {
        }
    }

    private static final class Redis implements Backend {
        private final LettuceConnectionFactory factory;
        private final StringRedisTemplate template;

        Redis(String url) {
            URI uri = URI.create(url);
            this.factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(uri.getHost(), uri.getPort()));
            factory.afterPropertiesSet();
            factory.start();
            this.template = new StringRedisTemplate(factory);
            template.afterPropertiesSet();
        }

        @Override
        public IdSegmentDistributor distributor(IdSegmentDistributorDefinition definition) {
            return new SpringRedisIdSegmentDistributor(definition.getNamespace(), definition.getName(), definition.getOffset(), definition.getStep(), template);
        }

        @Override
        public MachineLeaseStore leaseStore() {
            return new SpringRedisMachineLeaseStore(template, RedisCommandTimeout.of(Duration.ofSeconds(5)));
        }

        @Override
        public void close() {
            factory.destroy();
        }
    }

    private static final class Mongo implements Backend {
        private final MongoClient client;
        private final MongoDatabase database;

        Mongo(String url) {
            this.client = MongoClients.create(url);
            this.database = client.getDatabase("cosid_db");
            new MongoMachineInitializer(database).ensureMachineCollection();
        }

        @Override
        public IdSegmentDistributor distributor(IdSegmentDistributorDefinition definition) {
            return new MongoIdSegmentDistributorFactory(database).create(definition);
        }

        @Override
        public MachineLeaseStore leaseStore() {
            return new MongoMachineLeaseStore(database, Duration.ofSeconds(5));
        }

        @Override
        public void close() {
            client.close();
        }
    }

    private static final class Zookeeper implements Backend {
        private final CuratorFramework curator;
        private final RetryPolicy retry = new ExponentialBackoffRetry(100, 5, 500);

        Zookeeper(String connect) {
            this.curator = CuratorFrameworkFactory.newClient(connect, retry);
            curator.start();
        }

        @Override
        public IdSegmentDistributor distributor(IdSegmentDistributorDefinition definition) {
            return new ZookeeperIdSegmentDistributor(definition.getNamespace(), definition.getName(), definition.getOffset(), definition.getStep(), curator, retry,
                CallTimeout.of(Duration.ofSeconds(5)));
        }

        @Override
        public MachineLeaseStore leaseStore() {
            return new ZookeeperMachineLeaseStore(curator, CallTimeout.of(Duration.ofSeconds(5)));
        }

        @Override
        public void close() {
            curator.close();
        }
    }

    /** A {@link DataSource} over {@link DriverManager}, as the 4.x stores need one. */
    private static final class UrlDataSource implements DataSource {
        private final String url;

        UrlDataSource(String url) {
            this.url = url;
            // DriverManager only hands a driver to callers of its class loader: register this loader's copies.
            for (Driver driver : ServiceLoader.load(Driver.class, UrlDataSource.class.getClassLoader())) {
                try {
                    DriverManager.registerDriver(driver);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
