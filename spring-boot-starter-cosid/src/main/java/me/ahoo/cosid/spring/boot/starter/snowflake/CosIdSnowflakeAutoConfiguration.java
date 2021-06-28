package me.ahoo.cosid.spring.boot.starter.snowflake;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.SecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;
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
        SnowflakeIdProperties.InstanceId instanceId = snowflakeIdProperties.getInstanceId();
        Preconditions.checkNotNull(instanceId, "cosid.snowflake.instanceId can not be null.");

        boolean stable = false;
        if (Objects.nonNull(instanceId.getStable()) && instanceId.getStable()) {
            stable = instanceId.getStable();
        }

        if (!Strings.isNullOrEmpty(instanceId.getInstanceId())) {
            return DefaultInstanceId.of(instanceId.getInstanceId(), stable);
        }

        InetUtils.HostInfo hostInfo = inetUtils.findFirstNonLoopbackHostInfo();

        int port = (int) Systems.getCurrentProcessId();
        if (Objects.nonNull(instanceId.getPort()) && instanceId.getPort() > 0) {
            port = instanceId.getPort();
        }

        return DefaultInstanceId.of(hostInfo.getIpAddress(), port, stable);
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalMachineState localMachineState() {
        return new FileLocalMachineState(snowflakeIdProperties.getMachineState().getStateLocation());
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
    @ConditionalOnBean(value = {InstanceId.class, MachineIdDistributor.class})
    public MachineId machineId(InstanceId instanceId, MachineIdDistributor machineIdDistributor) {
        Integer machineBit = getMachineBit(snowflakeIdProperties.getShare());
        int machineId = machineIdDistributor.distribute(cosIdProperties.getNamespace(), machineBit, instanceId);
        return new MachineId(machineId);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(InstanceId.class)
    public SnowflakeId shareIdGenerator(MachineIdDistributor machineIdDistributor, InstanceId instanceId, IdGeneratorProvider idGeneratorProvider) {
        SnowflakeIdProperties.IdDefinition shareIdDefinition = snowflakeIdProperties.getShare();
        SnowflakeId shareIdGen = createIdGen(machineIdDistributor, instanceId, shareIdDefinition);
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

    private SnowflakeId createIdGen(MachineIdDistributor machineIdDistributor, InstanceId instanceId, SnowflakeIdProperties.IdDefinition idDefinition) {
        Integer machineBit = getMachineBit(idDefinition);
        int machineId = machineIdDistributor.distribute(cosIdProperties.getNamespace(), machineBit, instanceId);
        if (SnowflakeIdProperties.IdDefinition.TimestampUnit.SECOND.equals(idDefinition.getTimestampUnit())) {
            return new SecondSnowflakeId(idDefinition.getEpoch(), idDefinition.getTimestampBit(), machineBit, idDefinition.getSequenceBit(), machineId);
        }
        return new MillisecondSnowflakeId(idDefinition.getEpoch(), idDefinition.getTimestampBit(), machineBit, idDefinition.getSequenceBit(), machineId);
    }

    private Integer getMachineBit(SnowflakeIdProperties.IdDefinition idDefinition) {
        Integer machineBit = idDefinition.getMachineBit();
        if (Objects.isNull(machineBit) || machineBit <= 0) {
            machineBit = snowflakeIdProperties.getInstanceId().getMachineBit();
        }
        return machineBit;
    }

}
