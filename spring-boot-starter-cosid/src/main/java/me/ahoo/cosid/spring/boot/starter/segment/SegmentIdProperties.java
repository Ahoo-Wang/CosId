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
import me.ahoo.cosid.redis.RedisIdSegmentDistributor;
import me.ahoo.cosid.segment.SegmentChainId;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = SegmentIdProperties.PREFIX)
public class SegmentIdProperties {

    public final static String PREFIX = CosId.COSID_PREFIX + "segment";

    private boolean enabled;
    private Mode mode;
    private int step;
    private Duration timeout = RedisIdSegmentDistributor.DEFAULT_TIMEOUT;
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

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
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

        public Distributor() {
            this.redis = new Redis();
        }

        public Redis getRedis() {
            return redis;
        }

        public void setRedis(Redis redis) {
            this.redis = redis;
        }

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
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

        public enum Type {
            REDIS,
            MYSQL
        }
    }

    public static class IdDefinition {

        private Mode mode;
        private int offset = RedisIdSegmentDistributor.DEFAULT_OFFSET;
        private int step = RedisIdSegmentDistributor.DEFAULT_STEP;
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

        public Chain getChain() {
            return chain;
        }

        public void setChain(Chain chain) {
            this.chain = chain;
        }
    }
}
