package me.ahoo.cosid.spring.boot.starter;

import me.ahoo.cosid.MachineIdDistributor;
import me.ahoo.cosid.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.snowflake.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCosIdEnabled
@EnableConfigurationProperties(CosIdProperties.class)
public class CosIdAutoConfiguration {
    private final CosIdProperties cosIdProperties;

    public CosIdAutoConfiguration(CosIdProperties cosIdProperties) {
        this.cosIdProperties = cosIdProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    public MachineIdDistributor machineIdDistributor() {
        if (Objects.nonNull(cosIdProperties) && Objects.nonNull(cosIdProperties.getSnowflake())) {
            Integer machineId = cosIdProperties.getSnowflake().getMachineId();
            return new MachineIdDistributor.DefaultMachineIdDistributor(machineId);
        }
        return StatefulSetMachineIdDistributor.INSTANCE;
    }

    @Primary
    @Bean
    @ConditionalOnMissingBean
    public MillisecondSnowflakeId idGenerator(MachineIdDistributor machineIdDistributor) {
        Integer machineId = machineIdDistributor.distribute();
        return new MillisecondSnowflakeId(machineId);
    }

    @Bean
    @ConditionalOnBean(MillisecondSnowflakeId.class)
    public MillisecondSnowflakeIdStateParser millisecondSnowflakeIdStateParser(MillisecondSnowflakeId millisecondSnowflakeId) {
        return MillisecondSnowflakeIdStateParser.of(millisecondSnowflakeId);
    }

}
