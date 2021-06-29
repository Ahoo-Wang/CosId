package me.ahoo.cosid.spring.boot.starter.snowflake;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.*;
import me.ahoo.cosid.snowflake.machine.*;
import me.ahoo.cosid.snowflake.machine.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.redis.RedisMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosky.core.redis.RedisConnectionFactory;
import me.ahoo.cosky.core.util.Systems;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Nullable;
import java.time.Duration;
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
        SnowflakeIdProperties.Machine machine = snowflakeIdProperties.getMachine();
        Preconditions.checkNotNull(machine, "cosid.snowflake.machine can not be null.");

        boolean stable = false;
        if (Objects.nonNull(machine.getStable()) && machine.getStable()) {
            stable = machine.getStable();
        }

        if (!Strings.isNullOrEmpty(machine.getInstanceId())) {
            return DefaultInstanceId.of(machine.getInstanceId(), stable);
        }

        InetUtils.HostInfo hostInfo = inetUtils.findFirstNonLoopbackHostInfo();

        int port = (int) Systems.getCurrentProcessId();
        if (Objects.nonNull(machine.getPort()) && machine.getPort() > 0) {
            port = machine.getPort();
        }

        return DefaultInstanceId.of(hostInfo.getIpAddress(), port, stable);
    }

    @Bean
    @ConditionalOnMissingBean
    public MachineStateStorage localMachineState() {
        if (!snowflakeIdProperties.getMachine().getStateStorage().isEnabled()) {
            return MachineStateStorage.NONE;
        }
        return new LocalMachineStateStorage(snowflakeIdProperties.getMachine().getStateStorage().getLocal().getStateLocation());
    }

    @Bean
    @ConditionalOnMissingBean
    public ClockBackwardsSynchronizer clockBackwardsSynchronizer() {
        SnowflakeIdProperties.ClockBackwards clockBackwards = snowflakeIdProperties.getClockBackwards();
        Preconditions.checkNotNull(clockBackwards, "cosid.snowflake.clockBackwards can not be null.");
        return new DefaultClockBackwardsSynchronizer(clockBackwards.getSpinThreshold(), clockBackwards.getBrokenThreshold());
    }

    @Bean
    @ConditionalOnMissingBean
    public MachineIdDistributor machineIdDistributor(@Nullable RedisConnectionFactory redisConnectionFactory, MachineStateStorage localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        SnowflakeIdProperties.Machine.Distributor machineIdDistributor = snowflakeIdProperties.getMachine().getDistributor();
        switch (machineIdDistributor.getType()) {
            case MANUAL: {
                SnowflakeIdProperties.Machine.Manual manual = machineIdDistributor.getManual();
                Preconditions.checkNotNull(manual, "cosid.snowflake.machine.distributor.manual can not be null.");
                Integer machineId = manual.getMachineId();
                Preconditions.checkNotNull(machineId, "cosid.snowflake.machine.distributor.manual.machineId can not be null.");
                Preconditions.checkArgument(machineId >= 0, "cosid.snowflake.machine.distributor.manual.machineId can not be less than 0.");
                return new ManualMachineIdDistributor(machineId, localMachineState, clockBackwardsSynchronizer);
            }
            case STATEFUL_SET: {
                return new StatefulSetMachineIdDistributor(localMachineState, clockBackwardsSynchronizer);
            }
            case REDIS: {
                Preconditions.checkNotNull(redisConnectionFactory, "redisConnectionFactory can not be null.");
                Duration timeout = snowflakeIdProperties.getMachine().getDistributor().getRedis().getTimeout();
                return new RedisMachineIdDistributor(timeout, redisConnectionFactory.getShareAsyncCommands(), localMachineState, clockBackwardsSynchronizer);
            }
            default:
                throw new IllegalStateException("Unexpected value: " + machineIdDistributor.getType());
        }
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
    public SnowflakeId shareIdGenerator(MachineIdDistributor machineIdDistributor, InstanceId instanceId, IdGeneratorProvider idGeneratorProvider, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        SnowflakeIdProperties.IdDefinition shareIdDefinition = snowflakeIdProperties.getShare();
        SnowflakeId shareIdGen = createIdGen(machineIdDistributor, instanceId, shareIdDefinition, clockBackwardsSynchronizer);
        idGeneratorProvider.setShare(shareIdGen);
        if (Objects.isNull(snowflakeIdProperties.getProvider())) {
            return shareIdGen;
        }
        snowflakeIdProperties.getProvider().forEach((name, idDefinition) -> {
            IdGenerator idGenerator = createIdGen(machineIdDistributor, instanceId, idDefinition, clockBackwardsSynchronizer);
            idGeneratorProvider.set(name, idGenerator);
        });

        return shareIdGen;
    }

    private SnowflakeId createIdGen(MachineIdDistributor machineIdDistributor, InstanceId instanceId, SnowflakeIdProperties.IdDefinition idDefinition, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        long epoch = getEpoch(idDefinition);
        Integer machineBit = getMachineBit(idDefinition);
        SnowflakeId snowflakeId;
        int machineId = machineIdDistributor.distribute(cosIdProperties.getNamespace(), machineBit, instanceId);
        if (SnowflakeIdProperties.IdDefinition.TimestampUnit.SECOND.equals(idDefinition.getTimestampUnit())) {
            snowflakeId = new SecondSnowflakeId(epoch, idDefinition.getTimestampBit(), machineBit, idDefinition.getSequenceBit(), machineId);
        } else {
            snowflakeId = new MillisecondSnowflakeId(epoch, idDefinition.getTimestampBit(), machineBit, idDefinition.getSequenceBit(), machineId);
        }
        if (idDefinition.isClockSync()) {
            snowflakeId = new ClockSyncSnowflakeId(snowflakeId, clockBackwardsSynchronizer);
        }
        if (idDefinition.isFriendly()) {
            snowflakeId = new DefaultSnowflakeFriendlyId(snowflakeId);
        }
        return snowflakeId;
    }

    private Integer getMachineBit(SnowflakeIdProperties.IdDefinition idDefinition) {
        Integer machineBit = idDefinition.getMachineBit();
        if (Objects.isNull(machineBit) || machineBit <= 0) {
            machineBit = snowflakeIdProperties.getMachine().getMachineBit();
        }
        return machineBit;
    }

    private long getEpoch(SnowflakeIdProperties.IdDefinition idDefinition) {
        if (idDefinition.getEpoch() > 0) {
            return idDefinition.getEpoch();
        }
        return snowflakeIdProperties.getEpoch();
    }

}
