package me.ahoo.cosid.spring.boot.starter.snowflake;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = SnowflakeIdProperties.PREFIX)
public class SnowflakeIdProperties {
    public final static String PREFIX = CosId.COSID_PREFIX + "snowflake";

    private boolean enabled;
    private InstanceId instanceId;
    private Manual manual;

    private StatefulSet statefulSet;

    private Redis redis;

    private IdDefinition share;

    private Map<String, IdDefinition> provider;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SnowflakeIdProperties() {
        share = new IdDefinition();
    }

    public InstanceId getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(InstanceId instanceId) {
        this.instanceId = instanceId;
    }

    public Manual getManual() {
        return manual;
    }

    public void setManual(Manual manual) {
        this.manual = manual;
    }

    public StatefulSet getStatefulSet() {
        return statefulSet;
    }

    public void setStatefulSet(StatefulSet statefulSet) {
        this.statefulSet = statefulSet;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
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

    public static class InstanceId {
        private Boolean stable;

        private Integer port;

        public Boolean isStable() {
            return stable;
        }

        public void setStable(Boolean stable) {
            this.stable = stable;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }
    }

    public static class Manual {
        public static final String ENABLED_KEY = PREFIX + ".manual.enabled";
        private boolean enabled;
        private Integer machineId = 1;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getMachineId() {
            return machineId;
        }

        public void setMachineId(Integer machineId) {
            this.machineId = machineId;
        }
    }

    public static class StatefulSet {
        public static final String ENABLED_KEY = PREFIX + ".stateful-set.enabled";
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Redis {
        public static final String ENABLED_KEY = PREFIX + ".redis.enabled";
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class IdDefinition {
        private long epoch = CosId.COSID_EPOCH;
        private int timestampBit = MillisecondSnowflakeId.DEFAULT_TIMESTAMP_BIT;
        private int machineBit = MillisecondSnowflakeId.DEFAULT_MACHINE_BIT;
        private int sequenceBit = MillisecondSnowflakeId.DEFAULT_SEQUENCE_BIT;

        public long getEpoch() {
            return epoch;
        }

        public void setEpoch(long epoch) {
            this.epoch = epoch;
        }

        public int getTimestampBit() {
            return timestampBit;
        }

        public void setTimestampBit(int timestampBit) {
            this.timestampBit = timestampBit;
        }

        public int getMachineBit() {
            return machineBit;
        }

        public void setMachineBit(int machineBit) {
            this.machineBit = machineBit;
        }

        public int getSequenceBit() {
            return sequenceBit;
        }

        public void setSequenceBit(int sequenceBit) {
            this.sequenceBit = sequenceBit;
        }
    }
}
