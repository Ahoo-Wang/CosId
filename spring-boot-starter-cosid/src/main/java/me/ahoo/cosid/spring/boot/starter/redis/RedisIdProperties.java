package me.ahoo.cosid.spring.boot.starter.redis;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.redis.RedisIdGenerator;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * @author ahoo wang
 */

@ConfigurationProperties(prefix = RedisIdProperties.PREFIX)
public class RedisIdProperties {
    public final static String PREFIX = CosId.COSID_PREFIX + "redis";
    private boolean enabled;

    private IdDefinition share;

    private Map<String, IdDefinition> provider;

    public RedisIdProperties() {
        share = new IdDefinition();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public static class IdDefinition {

        private int step = RedisIdGenerator.DEFAULT_STEP;

        public int getStep() {
            return step;
        }

        public void setStep(int step) {
            this.step = step;
        }
    }
}
