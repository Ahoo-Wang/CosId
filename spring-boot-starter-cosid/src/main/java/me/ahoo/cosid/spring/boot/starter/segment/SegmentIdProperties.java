package me.ahoo.cosid.spring.boot.starter.segment;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.redis.RedisIdSegmentDistributor;
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
    private int step;
    private Duration timeout = RedisIdSegmentDistributor.DEFAULT_TIMEOUT;
    private Distributor distributor;

    private IdDefinition share;
    private Map<String, IdDefinition> provider;

    public SegmentIdProperties() {
        share = new IdDefinition();
        distributor = new Distributor();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
            REDIS
        }
    }

    public static class IdDefinition {

        private int offset = RedisIdSegmentDistributor.DEFAULT_OFFSET;
        private int step = RedisIdSegmentDistributor.DEFAULT_STEP;

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
    }
}
