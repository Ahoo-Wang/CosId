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

package me.ahoo.cosid.spring.boot.starter.snowflake;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.*;
import me.ahoo.cosid.snowflake.machine.*;
import me.ahoo.cosid.snowflake.machine.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.util.Systems;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
            return InstanceId.of(machine.getInstanceId(), stable);
        }

        InetUtils.HostInfo hostInfo = inetUtils.findFirstNonLoopbackHostInfo();

        int port = (int) Systems.getCurrentProcessId();
        if (Objects.nonNull(machine.getPort()) && machine.getPort() > 0) {
            port = machine.getPort();
        }

        return InstanceId.of(hostInfo.getIpAddress(), port, stable);
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
    @ConditionalOnProperty(value = SnowflakeIdProperties.Machine.Distributor.TYPE, matchIfMissing = true, havingValue = "manual")
    public ManualMachineIdDistributor machineIdDistributor(MachineStateStorage localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        SnowflakeIdProperties.Machine.Manual manual = snowflakeIdProperties.getMachine().getDistributor().getManual();
        Preconditions.checkNotNull(manual, "cosid.snowflake.machine.distributor.manual can not be null.");
        Integer machineId = manual.getMachineId();
        Preconditions.checkNotNull(machineId, "cosid.snowflake.machine.distributor.manual.machineId can not be null.");
        Preconditions.checkArgument(machineId >= 0, "cosid.snowflake.machine.distributor.manual.machineId can not be less than 0.");
        return new ManualMachineIdDistributor(machineId, localMachineState, clockBackwardsSynchronizer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = SnowflakeIdProperties.Machine.Distributor.TYPE, havingValue = "stateful_set")
    public StatefulSetMachineIdDistributor statefulSetMachineIdDistributor(MachineStateStorage localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        return new StatefulSetMachineIdDistributor(localMachineState, clockBackwardsSynchronizer);
    }

    @Bean
    @ConditionalOnMissingBean
    public LifecycleMachineIdDistributor lifecycleMachineIdDistributor(InstanceId instanceId, MachineIdDistributor machineIdDistributor) {
        return new LifecycleMachineIdDistributor(cosIdProperties, instanceId, machineIdDistributor);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public SnowflakeId shareSnowflakeId(MachineIdDistributor machineIdDistributor, InstanceId instanceId, IdGeneratorProvider idGeneratorProvider, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
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

    @Bean
    @ConditionalOnBean(value = SnowflakeId.class)
    public MachineId machineId(SnowflakeId snowflakeId) {
        int machineId = snowflakeId.getMachineId();
        return new MachineId(machineId);
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
