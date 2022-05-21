/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.converter.PrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.SnowflakeFriendlyIdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.DefaultClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.DefaultSnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeIdStateParser;
import me.ahoo.cosid.snowflake.StringSnowflakeId;
import me.ahoo.cosid.snowflake.machine.DefaultMachineIdGuarder;
import me.ahoo.cosid.snowflake.machine.InstanceId;
import me.ahoo.cosid.snowflake.machine.LocalMachineStateStorage;
import me.ahoo.cosid.snowflake.machine.MachineId;
import me.ahoo.cosid.snowflake.machine.MachineIdDistributor;
import me.ahoo.cosid.snowflake.machine.MachineIdGuarder;
import me.ahoo.cosid.snowflake.machine.MachineStateStorage;
import me.ahoo.cosid.snowflake.machine.ManualMachineIdDistributor;
import me.ahoo.cosid.snowflake.machine.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;
import me.ahoo.cosid.util.ProcessId;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

/**
 * CosId Snowflake AutoConfiguration.
 *
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
        
        int port = ProcessId.CURRENT.getProcessId();
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
    public CosIdLifecycleMachineIdDistributor cosIdLifecycleMachineIdDistributor(InstanceId instanceId, MachineIdDistributor machineIdDistributor) {
        return new CosIdLifecycleMachineIdDistributor(cosIdProperties, instanceId, machineIdDistributor);
    }
    
    private Duration getSafeGuardDuration() {
        SnowflakeIdProperties.Machine.Guarder guarder = snowflakeIdProperties.getMachine().getGuarder();
        if (guarder.isEnabled()) {
            return guarder.getSafeGuardDuration();
        }
        return MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public MachineIdGuarder machineIdGuarder(MachineIdDistributor machineIdDistributor) {
        SnowflakeIdProperties.Machine.Guarder guarder = snowflakeIdProperties.getMachine().getGuarder();
        if (!guarder.isEnabled()) {
            return MachineIdGuarder.NONE;
        }
        return new DefaultMachineIdGuarder(machineIdDistributor, DefaultMachineIdGuarder.executorService(), guarder.getInitialDelay(), guarder.getDelay(), getSafeGuardDuration());
    }
    
    @Bean
    @ConditionalOnMissingBean
    public CosIdLifecycleMachineIdGuarder cosIdLifecycleMachineIdGuarder(InstanceId instanceId, MachineIdGuarder machineIdGuarder) {
        return new CosIdLifecycleMachineIdGuarder(cosIdProperties, instanceId, machineIdGuarder);
    }
    
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public SnowflakeId shareSnowflakeId(MachineIdDistributor machineIdDistributor, InstanceId instanceId, IdGeneratorProvider idGeneratorProvider,
                                        ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        SnowflakeIdProperties.IdDefinition shareIdDefinition = snowflakeIdProperties.getShare();
        SnowflakeId shareIdGen = createIdGen(machineIdDistributor, instanceId, shareIdDefinition, clockBackwardsSynchronizer);
        idGeneratorProvider.setShare(shareIdGen);
        if (snowflakeIdProperties.getProvider().isEmpty()) {
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
        long machineId = snowflakeId.getMachineId();
        return new MachineId(machineId);
    }
    
    private SnowflakeId createIdGen(MachineIdDistributor machineIdDistributor, InstanceId instanceId, SnowflakeIdProperties.IdDefinition idDefinition,
                                    ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        long epoch = getEpoch(idDefinition);
        Integer machineBit = getMachineBit(idDefinition);
        SnowflakeId snowflakeId;
        int machineId = machineIdDistributor.distribute(cosIdProperties.getNamespace(), machineBit, instanceId, getSafeGuardDuration()).getMachineId();
        if (SnowflakeIdProperties.IdDefinition.TimestampUnit.SECOND.equals(idDefinition.getTimestampUnit())) {
            snowflakeId = new SecondSnowflakeId(epoch, idDefinition.getTimestampBit(), machineBit, idDefinition.getSequenceBit(), machineId);
        } else {
            snowflakeId = new MillisecondSnowflakeId(epoch, idDefinition.getTimestampBit(), machineBit, idDefinition.getSequenceBit(), machineId);
        }
        if (idDefinition.isClockSync()) {
            snowflakeId = new ClockSyncSnowflakeId(snowflakeId, clockBackwardsSynchronizer);
        }
        IdConverterDefinition converterDefinition = idDefinition.getConverter();
        final ZoneId zoneId = ZoneId.of(snowflakeIdProperties.getZoneId());
        IdConverter idConverter = ToStringIdConverter.INSTANCE;
        switch (converterDefinition.getType()) {
            case TO_STRING: {
                break;
            }
            case SNOWFLAKE_FRIENDLY: {
                idConverter = new SnowflakeFriendlyIdConverter(SnowflakeIdStateParser.of(snowflakeId, zoneId));
                break;
            }
            case RADIX: {
                IdConverterDefinition.Radix radix = converterDefinition.getRadix();
                idConverter = Radix62IdConverter.of(radix.isPadStart(), radix.getCharSize());
                break;
            }
            default:
                throw new IllegalStateException("Unexpected value: " + converterDefinition.getType());
        }
        if (!Strings.isNullOrEmpty(converterDefinition.getPrefix())) {
            idConverter = new PrefixIdConverter(converterDefinition.getPrefix(), idConverter);
        }
        if (idDefinition.isFriendly()) {
            SnowflakeIdStateParser snowflakeIdStateParser = SnowflakeIdStateParser.of(snowflakeId, zoneId);
            return new DefaultSnowflakeFriendlyId(snowflakeId, idConverter, snowflakeIdStateParser);
        }
        return new StringSnowflakeId(snowflakeId, idConverter);
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
