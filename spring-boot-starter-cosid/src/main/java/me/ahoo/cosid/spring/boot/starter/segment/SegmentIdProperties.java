/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.jdbc.JdbcIdSegmentDistributor;
import me.ahoo.cosid.jdbc.JdbcIdSegmentInitializer;
import me.ahoo.cosid.redis.RedisIdSegmentDistributor;
import me.ahoo.cosid.segment.SegmentChainId;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

import static me.ahoo.cosid.segment.IdSegment.TIME_TO_LIVE_FOREVER;

/**
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = SegmentIdProperties.PREFIX)
public class SegmentIdProperties {

    public final static String PREFIX = CosId.COSID_PREFIX + "segment";

    private boolean enabled;
    private Mode mode = Mode.CHAIN;
    private int step;
    /**
     * idSegment time to live
     */
    private long ttl = TIME_TO_LIVE_FOREVER;
    private Distributor distributor;
    private Chain chain;
    private IdDefinition share;
    private Map<String, IdDefinition> provider;

    public SegmentIdProperties() {
        share = new IdDefinition();
        distributor = new Distributor();
        chain = new Chain();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public Distributor getDistributor() {
        return distributor;
    }

    public void setDistributor(Distributor distributor) {
        this.distributor = distributor;
    }

    public Chain getChain() {
        return chain;
    }

    public void setChain(Chain chain) {
        this.chain = chain;
    }

    public IdDefinition getShare() {
        return share;
    }

    public void setShare(IdDefinition share) {
        this.share = share;
    }

    public Map<String, IdDefinition> getProvider() {
        return provider;
    }

    public void setProvider(Map<String, IdDefinition> provider) {
        this.provider = provider;
    }

    public enum Mode {
        DEFAULT,
        CHAIN
    }

    public static class Chain {
        private int safeDistance = SegmentChainId.DEFAULT_SAFE_DISTANCE;
        private Duration prefetchPeriod = SegmentChainId.DEFAULT_PREFETCH_PERIOD;

        public int getSafeDistance() {
            return safeDistance;
        }

        public void setSafeDistance(int safeDistance) {
            this.safeDistance = safeDistance;
        }

        public Duration getPrefetchPeriod() {
            return prefetchPeriod;
        }

        public void setPrefetchPeriod(Duration prefetchPeriod) {
            this.prefetchPeriod = prefetchPeriod;
        }
    }

    public static class Distributor {
        public static final String TYPE = PREFIX + ".distributor.type";
        private Type type = Type.REDIS;
        private Redis redis;
        private Jdbc jdbc;

        public Distributor() {
            this.redis = new Redis();
            this.jdbc = new Jdbc();
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

        public static class Redis {

            private Duration timeout = RedisIdSegmentDistributor.DEFAULT_TIMEOUT;

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

        public enum Type {
            REDIS,
            JDBC
        }
    }

    public static class IdDefinition {

        private Mode mode;
        private int offset = RedisIdSegmentDistributor.DEFAULT_OFFSET;
        private int step = RedisIdSegmentDistributor.DEFAULT_STEP;
        /**
         * idSegmentTtl
         */
        private Long ttl;
        private Chain chain;

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public int getOffset() {
            return offset;
        }

        public void setOffset(int offset) {
            this.offset = offset;
        }

        public int getStep() {
            return step;
        }

        public void setStep(int step) {
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
    }
}
