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

    private Provider share;

    private Map<String, Provider> providers;

    public RedisIdProperties() {
        share = new Provider();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Provider getShare() {
        return share;
    }

    public void setShare(Provider share) {
        this.share = share;
    }

    public Map<String, Provider> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers;
    }

    public static class Provider {

        private int step = RedisIdGenerator.DEFAULT_STEP;

        public int getStep() {
            return step;
        }

        public void setStep(int step) {
            this.step = step;
        }
    }
}
