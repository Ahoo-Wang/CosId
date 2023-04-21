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
import me.ahoo.cosid.converter.PrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.SnowflakeFriendlyIdConverter;
import me.ahoo.cosid.converter.SuffixIdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineId;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.DefaultSnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeIdStateParser;
import me.ahoo.cosid.snowflake.StringSnowflakeId;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;

import com.google.common.base.Strings;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.ZoneId;

public class SnowflakeIdBeanRegistrar implements InitializingBean {
    private final MachineProperties machineProperties;
    private final SnowflakeIdProperties snowflakeIdProperties;
    private final MachineId machineId;
    private final IdGeneratorProvider idGeneratorProvider;
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;
    private final ConfigurableApplicationContext applicationContext;
    
    public SnowflakeIdBeanRegistrar(MachineProperties machineProperties,
                                    SnowflakeIdProperties snowflakeIdProperties,
                                    MachineId machineId,
                                    IdGeneratorProvider idGeneratorProvider,
                                    ClockBackwardsSynchronizer clockBackwardsSynchronizer,
                                    ConfigurableApplicationContext applicationContext) {
        this.machineProperties = machineProperties;
        this.snowflakeIdProperties = snowflakeIdProperties;
        this.machineId = machineId;
        this.idGeneratorProvider = idGeneratorProvider;
        this.clockBackwardsSynchronizer = clockBackwardsSynchronizer;
        this.applicationContext = applicationContext;
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        register();
    }
    
    public void register() {
        SnowflakeIdProperties.ShardIdDefinition shareIdDefinition = snowflakeIdProperties.getShare();
        if (shareIdDefinition.isEnabled()) {
            registerIdDefinition(IdGeneratorProvider.SHARE, shareIdDefinition);
        }
        snowflakeIdProperties.getProvider().forEach(this::registerIdDefinition);
    }
    
    private void registerIdDefinition(String name, SnowflakeIdProperties.IdDefinition idDefinition) {
        SnowflakeId idGenerator = createIdGen(machineId, idDefinition, clockBackwardsSynchronizer);
        registerSnowflakeId(name, idGenerator);
    }
    
    private void registerSnowflakeId(String name, SnowflakeId snowflakeId) {
        if (!idGeneratorProvider.get(name).isPresent()) {
            idGeneratorProvider.set(name, snowflakeId);
        }
        
        String beanName = name + "SnowflakeId";
        applicationContext.getBeanFactory().registerSingleton(beanName, snowflakeId);
    }
    
    private SnowflakeId createIdGen(final MachineId machineId, SnowflakeIdProperties.IdDefinition idDefinition,
                                    ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        long epoch = getEpoch(idDefinition);
        int machineBit = machineProperties.getMachineBit();
        SnowflakeId snowflakeId;
        if (SnowflakeIdProperties.IdDefinition.TimestampUnit.SECOND.equals(idDefinition.getTimestampUnit())) {
            snowflakeId = new SecondSnowflakeId(epoch, idDefinition.getTimestampBit(), machineBit, idDefinition.getSequenceBit(), machineId.getMachineId(), idDefinition.getSequenceResetThreshold());
        } else {
            snowflakeId =
                new MillisecondSnowflakeId(epoch, idDefinition.getTimestampBit(), machineBit, idDefinition.getSequenceBit(), machineId.getMachineId(), idDefinition.getSequenceResetThreshold());
        }
        if (idDefinition.isClockSync()) {
            snowflakeId = new ClockSyncSnowflakeId(snowflakeId, clockBackwardsSynchronizer);
        }
        IdConverterDefinition converterDefinition = idDefinition.getConverter();
        final ZoneId zoneId = ZoneId.of(snowflakeIdProperties.getZoneId());
        IdConverter idConverter = ToStringIdConverter.INSTANCE;
        switch (converterDefinition.getType()) {
            case TO_STRING: {
                IdConverterDefinition.ToString toString = converterDefinition.getToString();
                if (toString != null) {
                    idConverter = new ToStringIdConverter(toString.isPadStart(), toString.getCharSize());
                }
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
        if (!Strings.isNullOrEmpty(converterDefinition.getSuffix())) {
            idConverter = new SuffixIdConverter(converterDefinition.getSuffix(), idConverter);
        }
        if (idDefinition.isFriendly()) {
            SnowflakeIdStateParser snowflakeIdStateParser = SnowflakeIdStateParser.of(snowflakeId, zoneId);
            return new DefaultSnowflakeFriendlyId(snowflakeId, idConverter, snowflakeIdStateParser);
        }
        return new StringSnowflakeId(snowflakeId, idConverter);
    }
    
    private long getEpoch(SnowflakeIdProperties.IdDefinition idDefinition) {
        if (idDefinition.getEpoch() > 0) {
            return idDefinition.getEpoch();
        }
        return snowflakeIdProperties.getEpoch();
    }
    
}
