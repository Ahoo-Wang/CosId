package me.ahoo.cosid.spring.boot.starter.snowflake;

import com.google.common.base.Preconditions;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.machine.*;
import me.ahoo.cosid.snowflake.machine.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.redis.RedisMachineIdDistributor;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import me.ahoo.cosky.core.util.Systems;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdSnowflakeEnabled
@EnableConfigurationProperties(SnowflakeIdProperties.class)
public class CosIdSnowflakeAutoConfiguration {
    private final CosIdProperties cosIdProperties;
    private final SnowflakeIdProperties snowflakeIdProperties;

    public CosIdSnowflakeAutoConfiguration(CosIdProperties cosIdProperties, SnowflakeIdProperties snowflakeIdProperties) {
        this.cosIdProperties = cosIdProperties;
        this.snowflakeIdProperties = snowflakeIdProperties;
    }


    @Bean
    @ConditionalOnMissingBean
    public InstanceId instanceId(InetUtils inetUtils) {
        InetUtils.HostInfo hostInfo = inetUtils.findFirstNonLoopbackHostInfo();
        int port = (int) Systems.getCurrentProcessId();
        boolean stable = false;
        SnowflakeIdProperties.InstanceId instanceId = snowflakeIdProperties.getInstanceId();
        if (Objects.nonNull(instanceId)) {
            if (Objects.nonNull(instanceId.getPort()) && instanceId.getPort() > 0) {
                port = instanceId.getPort();
            }
            if (Objects.nonNull(instanceId.isStable()) && instanceId.isStable()) {
                stable = instanceId.isStable();
            }
        }

        return DefaultInstanceId.of(hostInfo.getIpAddress(), port, stable);
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalMachineState localMachineState() {
        return LocalMachineState.FILE;
    }

    @Bean
    @ConditionalOnMissingBean
    public ClockBackwardsSynchronizer clockBackwardsSynchronizer() {
        return ClockBackwardsSynchronizer.DEFAULT;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = SnowflakeIdProperties.StatefulSet.ENABLED_KEY, havingValue = "true")
    public StatefulSetMachineIdDistributor statefulSetMachineIdDistributor(LocalMachineState localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        return new StatefulSetMachineIdDistributor(localMachineState, clockBackwardsSynchronizer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = SnowflakeIdProperties.Manual.ENABLED_KEY, havingValue = "true")
    public MachineIdDistributor manualMachineIdDistributor(LocalMachineState localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        Integer machineId = snowflakeIdProperties.getManual().getMachineId();
        Preconditions.checkNotNull(machineId, "cosid.snowflake.manual.machineId can not be null.");
        return new ManualMachineIdDistributor(machineId, localMachineState, clockBackwardsSynchronizer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnProperty(value = SnowflakeIdProperties.Redis.ENABLED_KEY, havingValue = "true")
    public RedisMachineIdDistributor redisMachineIdDistributor(RedisConnectionFactory redisConnectionFactory, LocalMachineState localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        return new RedisMachineIdDistributor(redisConnectionFactory.getShareAsyncCommands(), localMachineState, clockBackwardsSynchronizer);
    }

    @Bean
    @ConditionalOnBean(value = MachineIdDistributor.class)
    public LifecycleMachineIdDistributor lifecycleMachineIdDistributor(InstanceId instanceId, MachineIdDistributor machineIdDistributor) {
        return new LifecycleMachineIdDistributor(cosIdProperties, instanceId, machineIdDistributor);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(InstanceId.class)
    public MillisecondSnowflakeId shareIdGenerator(MachineIdDistributor machineIdDistributor, InstanceId instanceId, IdGeneratorProvider idGeneratorProvider) {
        SnowflakeIdProperties.IdDefinition shareIdDefinition = snowflakeIdProperties.getShare();
        MillisecondSnowflakeId shareIdGen = createIdGen(machineIdDistributor, instanceId, shareIdDefinition);
        idGeneratorProvider.setShare(shareIdGen);
        if (Objects.isNull(snowflakeIdProperties.getProvider())) {
            return shareIdGen;
        }
        snowflakeIdProperties.getProvider().forEach((name, idDefinition) -> {
            IdGenerator idGenerator = createIdGen(machineIdDistributor, instanceId, idDefinition);
            idGeneratorProvider.set(name, idGenerator);
        });

        return shareIdGen;
    }

    private MillisecondSnowflakeId createIdGen(MachineIdDistributor machineIdDistributor, InstanceId instanceId, SnowflakeIdProperties.IdDefinition idDefinition) {
        int machineId = machineIdDistributor.distribute(cosIdProperties.getNamespace(), idDefinition.getMachineBit(), instanceId);
        return new MillisecondSnowflakeId(idDefinition.getEpoch(), idDefinition.getTimestampBit(), idDefinition.getMachineBit(), idDefinition.getSequenceBit(), machineId);
    }

}
