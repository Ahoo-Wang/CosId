package me.ahoo.cosid.spring.boot.starter;

import me.ahoo.cosid.CosId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = CosId.COSID)
public class CosIdProperties {
    private Snowflake snowflake;

    public Snowflake getSnowflake() {
        return snowflake;
    }

    public void setSnowflake(Snowflake snowflake) {
        this.snowflake = snowflake;
    }

    public static class Snowflake {
        private Integer machineId = 1;

        public Integer getMachineId() {
            return machineId;
        }

        public void setMachineId(Integer machineId) {
            this.machineId = machineId;
        }
    }
}
