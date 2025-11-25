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

import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributor;
import me.ahoo.cosid.jdbc.JdbcIdSegmentInitializer;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import jakarta.annotation.Nonnull;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for segment-based ID generation in Spring Boot applications.
 *
 * <p>This class defines properties for configuring segment ID generators, which provide
 * high-performance, database-friendly ID generation. Segment IDs are allocated in batches
 * (segments) to reduce database contention and improve performance.</p>
 *
 * <p>The configuration supports different modes:
 * <ul>
 *   <li>SEGMENT - Basic segment allocation</li>
 *   <li>CHAIN - Chain-based segment allocation with prefetching</li>
 * </ul>
 *
 * <p>Multiple distributor types are supported for coordinating segment allocation:
 * <ul>
 *   <li>REDIS - Redis-based coordination</li>
 *   <li>JDBC - Database-based coordination</li>
 *   <li>MONGO - MongoDB-based coordination</li>
 *   <li>ZOOKEEPER - ZooKeeper-based coordination</li>
 *   <li>PROXY - Proxy service-based coordination</li>
 * </ul>
 *
 * <p>Example configuration:
 * <pre>{@code
 * cosid:
 *   segment:
 *     enabled: true
 *     mode: CHAIN
 *     ttl: 3600
 *     distributor:
 *       type: REDIS
 *       redis:
 *         timeout: 2s
 * }</pre>
 *
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = SegmentIdProperties.PREFIX)
public class SegmentIdProperties {

    /**
     * The configuration property prefix for segment ID properties.
     */
    public static final String PREFIX = CosId.COSID_PREFIX + "segment";

    /**
     * Whether segment ID generation is enabled.
     * Default is false.
     */
    private boolean enabled = false;

    /**
     * The mode of segment ID generation.
     * Default is CHAIN.
     */
    private Mode mode = Mode.CHAIN;

    /**
     * Time to live for ID segments in seconds.
     * Default is {@link IdSegment#TIME_TO_LIVE_FOREVER} (never expires).
     */
    private long ttl = TIME_TO_LIVE_FOREVER;

    /**
     * Configuration for the segment distributor.
     */
    private Distributor distributor;

    /**
     * Configuration for chain-based segment generation.
     */
    private Chain chain;

    /**
     * Configuration for shared ID definitions.
     */
    private ShardIdDefinition share;

    /**
     * Map of named ID definitions for different generators.
     */
    private Map<String, IdDefinition> provider;

    /**
     * Constructs a new SegmentIdProperties instance with default configurations.
     *
     * <p>Initializes all nested configuration objects with their default values.</p>
     */
    public SegmentIdProperties() {
        share = new ShardIdDefinition();
        distributor = new Distributor();
        chain = new Chain();
        provider = new HashMap<>();
    }

    /**
     * Checks if segment ID generation is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether segment ID generation should be enabled.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the mode of segment ID generation.
     *
     * @return the segment mode
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Sets the mode of segment ID generation.
     *
     * @param mode the segment mode to set
     */
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /**
     * Gets the time to live for ID segments.
     *
     * @return the TTL in seconds
     */
    public long getTtl() {
        return ttl;
    }

    /**
     * Sets the time to live for ID segments.
     *
     * @param ttl the TTL in seconds to set
     */
    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    /**
     * Gets the distributor configuration.
     *
     * @return the distributor configuration
     */
    public Distributor getDistributor() {
        return distributor;
    }

    /**
     * Sets the distributor configuration.
     *
     * @param distributor the distributor configuration to set
     */
    public void setDistributor(Distributor distributor) {
        this.distributor = distributor;
    }

    /**
     * Gets the chain configuration.
     *
     * @return the chain configuration
     */
    public Chain getChain() {
        return chain;
    }

    /**
     * Sets the chain configuration.
     *
     * @param chain the chain configuration to set
     */
    public void setChain(Chain chain) {
        this.chain = chain;
    }

    /**
     * Gets the shared ID definition configuration.
     *
     * @return the shared ID definition
     */
    public ShardIdDefinition getShare() {
        return share;
    }

    /**
     * Sets the shared ID definition configuration.
     *
     * @param share the shared ID definition to set
     */
    public void setShare(ShardIdDefinition share) {
        this.share = share;
    }

    /**
     * Gets the map of named ID definitions.
     *
     * @return the provider map of ID definitions
     */
    @Nonnull
    public Map<String, IdDefinition> getProvider() {
        return provider;
    }

    /**
     * Sets the map of named ID definitions.
     *
     * @param provider the provider map to set
     */
    public void setProvider(Map<String, IdDefinition> provider) {
        this.provider = provider;
    }

    /**
     * Enumeration of supported segment ID generation modes.
     */
    public enum Mode {
        /**
         * Basic segment mode that allocates IDs in fixed-size segments.
         */
        SEGMENT,

        /**
         * Chain mode that uses a chain of segments with prefetching for better performance.
         */
        CHAIN
    }

    /**
     * Configuration for chain-based segment ID generation.
     *
     * <p>Chain mode provides better performance by maintaining a chain of segments
     * and prefetching new segments before the current one is exhausted.</p>
     */
    public static class Chain {
        /**
         * The safe distance for chain-based generation.
         * Default is {@link SegmentChainId#DEFAULT_SAFE_DISTANCE}.
         */
        private int safeDistance = SegmentChainId.DEFAULT_SAFE_DISTANCE;

        /**
         * Configuration for the prefetch worker.
         */
        private PrefetchWorker prefetchWorker;

        /**
         * Constructs a new Chain configuration with default prefetch worker.
         */
        public Chain() {
            prefetchWorker = new PrefetchWorker();
        }

        /**
         * Gets the safe distance for chain generation.
         *
         * @return the safe distance
         */
        public int getSafeDistance() {
            return safeDistance;
        }

        /**
         * Sets the safe distance for chain generation.
         *
         * @param safeDistance the safe distance to set
         */
        public void setSafeDistance(int safeDistance) {
            this.safeDistance = safeDistance;
        }

        /**
         * Gets the prefetch worker configuration.
         *
         * @return the prefetch worker configuration
         */
        public PrefetchWorker getPrefetchWorker() {
            return prefetchWorker;
        }

        /**
         * Sets the prefetch worker configuration.
         *
         * @param prefetchWorker the prefetch worker configuration to set
         */
        public void setPrefetchWorker(PrefetchWorker prefetchWorker) {
            this.prefetchWorker = prefetchWorker;
        }

        /**
         * Configuration for the prefetch worker that manages segment prefetching.
         */
        public static class PrefetchWorker {

            /**
             * The period between prefetch operations.
             * Default is {@link PrefetchWorkerExecutorService#DEFAULT_PREFETCH_PERIOD}.
             */
            private Duration prefetchPeriod = PrefetchWorkerExecutorService.DEFAULT_PREFETCH_PERIOD;

            /**
             * The core pool size for the prefetch worker executor.
             * Default is the number of available processors.
             */
            private int corePoolSize = Runtime.getRuntime().availableProcessors();

            /**
             * Whether to register a shutdown hook for graceful shutdown.
             * Default is true.
             */
            private boolean shutdownHook = true;

            /**
             * Gets the prefetch period.
             *
             * @return the prefetch period duration
             */
            public Duration getPrefetchPeriod() {
                return prefetchPeriod;
            }

            /**
             * Sets the prefetch period.
             *
             * @param prefetchPeriod the prefetch period to set
             */
            public void setPrefetchPeriod(Duration prefetchPeriod) {
                this.prefetchPeriod = prefetchPeriod;
            }

            /**
             * Gets the core pool size for the executor.
             *
             * @return the core pool size
             */
            public int getCorePoolSize() {
                return corePoolSize;
            }

            /**
             * Sets the core pool size for the executor.
             *
             * @param corePoolSize the core pool size to set
             */
            public void setCorePoolSize(int corePoolSize) {
                this.corePoolSize = corePoolSize;
            }

            /**
             * Checks if shutdown hook is enabled.
             *
             * @return true if shutdown hook is enabled, false otherwise
             */
            public boolean isShutdownHook() {
                return shutdownHook;
            }

            /**
             * Sets whether to enable shutdown hook.
             *
             * @param shutdownHook true to enable shutdown hook, false to disable
             */
            public void setShutdownHook(boolean shutdownHook) {
                this.shutdownHook = shutdownHook;
            }
        }
    }

    public static class Distributor {
        public static final String TYPE = PREFIX + ".distributor.type";
        private Type type = Type.REDIS;
        private Redis redis;
        private Jdbc jdbc;
        private Mongo mongo;

        public Distributor() {
            this.redis = new Redis();
            this.jdbc = new Jdbc();
            this.mongo = new Mongo();
        }

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public Redis getRedis() {
            return redis;
        }

        public void setRedis(Redis redis) {
            this.redis = redis;
        }

        public Jdbc getJdbc() {
            return jdbc;
        }

        public void setJdbc(Jdbc jdbc) {
            this.jdbc = jdbc;
        }

        public Mongo getMongo() {
            return mongo;
        }

        public void setMongo(Mongo mongo) {
            this.mongo = mongo;
        }

        public static class Redis {

            private Duration timeout = Duration.ofSeconds(1);

            public Duration getTimeout() {
                return timeout;
            }

            public void setTimeout(Duration timeout) {
                this.timeout = timeout;
            }
        }

        public static class Jdbc {

            private String incrementMaxIdSql = JdbcIdSegmentDistributor.INCREMENT_MAX_ID_SQL;
            private String fetchMaxIdSql = JdbcIdSegmentDistributor.FETCH_MAX_ID_SQL;
            private boolean enableAutoInitCosidTable = false;
            private String initCosidTableSql = JdbcIdSegmentInitializer.INIT_COSID_TABLE_SQL;
            private boolean enableAutoInitIdSegment = true;
            private String initIdSegmentSql = JdbcIdSegmentInitializer.INIT_ID_SEGMENT_SQL;

            public String getIncrementMaxIdSql() {
                return incrementMaxIdSql;
            }

            public void setIncrementMaxIdSql(String incrementMaxIdSql) {
                this.incrementMaxIdSql = incrementMaxIdSql;
            }

            public String getFetchMaxIdSql() {
                return fetchMaxIdSql;
            }

            public void setFetchMaxIdSql(String fetchMaxIdSql) {
                this.fetchMaxIdSql = fetchMaxIdSql;
            }

            public boolean isEnableAutoInitCosidTable() {
                return enableAutoInitCosidTable;
            }

            public void setEnableAutoInitCosidTable(boolean enableAutoInitCosidTable) {
                this.enableAutoInitCosidTable = enableAutoInitCosidTable;
            }

            public String getInitCosidTableSql() {
                return initCosidTableSql;
            }

            public void setInitCosidTableSql(String initCosidTableSql) {
                this.initCosidTableSql = initCosidTableSql;
            }

            public boolean isEnableAutoInitIdSegment() {
                return enableAutoInitIdSegment;
            }

            public void setEnableAutoInitIdSegment(boolean enableAutoInitIdSegment) {
                this.enableAutoInitIdSegment = enableAutoInitIdSegment;
            }

            public String getInitIdSegmentSql() {
                return initIdSegmentSql;
            }

            public void setInitIdSegmentSql(String initIdSegmentSql) {
                this.initIdSegmentSql = initIdSegmentSql;
            }

        }

        public static class Mongo {
            private String database = "cosid_db";

            public String getDatabase() {
                return database;
            }

            public void setDatabase(String database) {
                this.database = database;
            }
        }

        public enum Type {
            REDIS,
            JDBC,
            MONGO,
            ZOOKEEPER,
            PROXY
        }
    }

    public static class IdDefinition {
        private String namespace;
        private Mode mode;
        private long offset = IdSegmentDistributor.DEFAULT_OFFSET;
        private long step = IdSegmentDistributor.DEFAULT_STEP;
        /**
         * idSegment Ttl.
         * unit {@link java.util.concurrent.TimeUnit#SECONDS}
         */
        private Long ttl;
        private Chain chain;
        @NestedConfigurationProperty
        private IdConverterDefinition converter = new IdConverterDefinition();
        private Group group = new Group();

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public long getOffset() {
            return offset;
        }

        public void setOffset(long offset) {
            this.offset = offset;
        }

        public long getStep() {
            return step;
        }

        public void setStep(long step) {
            this.step = step;
        }

        public Long getTtl() {
            return ttl;
        }

        public void setTtl(Long ttl) {
            this.ttl = ttl;
        }

        public Chain getChain() {
            return chain;
        }

        public void setChain(Chain chain) {
            this.chain = chain;
        }

        public IdConverterDefinition getConverter() {
            return converter;
        }

        public void setConverter(IdConverterDefinition converter) {
            this.converter = converter;
        }

        public Group getGroup() {
            return group;
        }

        public void setGroup(Group group) {
            this.group = group;
        }

        public static class Group {
            private GroupBy by = GroupBy.NEVER;
            private String pattern;

            public GroupBy getBy() {
                return by;
            }

            public Group setBy(GroupBy by) {
                this.by = by;
                return this;
            }

            public String getPattern() {
                return pattern;
            }

            public void setPattern(String pattern) {
                this.pattern = pattern;
            }
        }

        public enum GroupBy {
            YEAR,
            YEAR_MONTH,
            YEAR_MONTH_DAY,
            NEVER
        }
    }

    public static class ShardIdDefinition extends IdDefinition {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
