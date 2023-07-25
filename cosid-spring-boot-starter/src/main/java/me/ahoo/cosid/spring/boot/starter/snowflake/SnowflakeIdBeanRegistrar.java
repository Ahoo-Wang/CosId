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

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;
import me.ahoo.cosid.spring.boot.starter.Namespaces;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;

import com.google.common.base.MoreObjects;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.Nullable;

import java.time.ZoneId;

public class SnowflakeIdBeanRegistrar implements InitializingBean {
    private final CosIdProperties cosIdProperties;
    private final MachineProperties machineProperties;
    private final SnowflakeIdProperties snowflakeIdProperties;
    private final InstanceId instanceId;
    private final IdGeneratorProvider idGeneratorProvider;
    private final MachineIdDistributor machineIdDistributor;
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;
    private final ConfigurableApplicationContext applicationContext;
    @Nullable
    private final CustomizeSnowflakeIdProvider customizeSnowflakeIdProvider;
    
    public SnowflakeIdBeanRegistrar(CosIdProperties cosIdProperties,
                                    MachineProperties machineProperties,
                                    SnowflakeIdProperties snowflakeIdProperties,
                                    InstanceId instanceId,
                                    IdGeneratorProvider idGeneratorProvider,
                                    MachineIdDistributor machineIdDistributor,
                                    ClockBackwardsSynchronizer clockBackwardsSynchronizer,
                                    ConfigurableApplicationContext applicationContext,
                                    @Nullable CustomizeSnowflakeIdProvider customizeSnowflakeIdProvider) {
        this.cosIdProperties = cosIdProperties;
        this.machineProperties = machineProperties;
        this.snowflakeIdProperties = snowflakeIdProperties;
        this.instanceId = instanceId;
        this.idGeneratorProvider = idGeneratorProvider;
        this.machineIdDistributor = machineIdDistributor;
        this.clockBackwardsSynchronizer = clockBackwardsSynchronizer;
        this.applicationContext = applicationContext;
        this.customizeSnowflakeIdProvider = customizeSnowflakeIdProvider;
    }
    
    @Override
    public void afterPropertiesSet() {
        register();
    }
    
    public void register() {
        if (customizeSnowflakeIdProvider != null) {
            customizeSnowflakeIdProvider.customize(snowflakeIdProperties.getProvider());
        }
        SnowflakeIdProperties.ShardIdDefinition shareIdDefinition = snowflakeIdProperties.getShare();
        if (shareIdDefinition.isEnabled()) {
            registerIdDefinition(IdGeneratorProvider.SHARE, shareIdDefinition);
        }
        snowflakeIdProperties.getProvider().forEach(this::registerIdDefinition);
    }
    
    private void registerIdDefinition(String name, SnowflakeIdProperties.IdDefinition idDefinition) {
        SnowflakeId idGenerator = createIdGen(idDefinition, clockBackwardsSynchronizer);
        registerSnowflakeId(name, idGenerator);
    }
    
    private void registerSnowflakeId(String name, SnowflakeId snowflakeId) {
        if (!idGeneratorProvider.get(name).isPresent()) {
            idGeneratorProvider.set(name, snowflakeId);
        }
        
        String beanName = name + "SnowflakeId";
        applicationContext.getBeanFactory().registerSingleton(beanName, snowflakeId);
    }
    
    private SnowflakeId createIdGen(SnowflakeIdProperties.IdDefinition idDefinition,
                                    ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        long epoch = getEpoch(idDefinition);
        int machineBit = MoreObjects.firstNonNull(idDefinition.getMachineBit(), machineProperties.getMachineBit());
        String namespace = Namespaces.firstNotBlank(idDefinition.getNamespace(), cosIdProperties.getNamespace());
        int machineId = machineIdDistributor.distribute(namespace, machineBit, instanceId, machineProperties.getSafeGuardDuration()).getMachineId();
        
        SnowflakeId snowflakeId;
        if (SnowflakeIdProperties.IdDefinition.TimestampUnit.SECOND.equals(idDefinition.getTimestampUnit())) {
            snowflakeId = new SecondSnowflakeId(epoch, idDefinition.getTimestampBit(), machineBit, idDefinition.getSequenceBit(), machineId, idDefinition.getSequenceResetThreshold());
        } else {
            snowflakeId =
                new MillisecondSnowflakeId(epoch, idDefinition.getTimestampBit(), machineBit, idDefinition.getSequenceBit(), machineId, idDefinition.getSequenceResetThreshold());
        }
        if (idDefinition.isClockSync()) {
            snowflakeId = new ClockSyncSnowflakeId(snowflakeId, clockBackwardsSynchronizer);
        }
        IdConverterDefinition converterDefinition = idDefinition.getConverter();
        final ZoneId zoneId = ZoneId.of(snowflakeIdProperties.getZoneId());
        return new SnowflakeIdConverterDecorator(snowflakeId, converterDefinition, zoneId, idDefinition.isFriendly()).decorate();
    }
    
    private long getEpoch(SnowflakeIdProperties.IdDefinition idDefinition) {
        if (idDefinition.getEpoch() > 0) {
            return idDefinition.getEpoch();
        }
        return snowflakeIdProperties.getEpoch();
    }
    
}
