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

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.GuardDistribute;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.LeasedSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.IdConverterDefinition;
import me.ahoo.cosid.spring.boot.starter.Namespaces;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;

import com.google.common.base.MoreObjects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SnowflakeIdBeanRegistrar implements InitializingBean, DisposableBean {
    /**
     * The bean name, the factory bean of the {@code {name}SnowflakeId} bean definitions.
     */
    public static final String BEAN_NAME = "snowflakeIdBeanRegistrar";
    private final CosIdProperties cosIdProperties;
    private final MachineProperties machineProperties;
    private final SnowflakeIdProperties snowflakeIdProperties;
    private final InstanceId instanceId;
    private final IdGeneratorProvider idGeneratorProvider;
    private final GuardDistribute guardDistribute;
    private final ClockBackwardsSynchronizer clockBackwardsSynchronizer;
    @Nullable
    private final BeanDefinitionRegistry beanDefinitions;
    private final Map<String, SnowflakeId> created = new ConcurrentHashMap<>();
    
    /**
     * Create the factory of the snowflake ids: runs every {@link CustomizeSnowflakeIdProperties} on the properties first.
     *
     * @param beanDefinitions the registry of the {@code {name}SnowflakeId} bean definitions, {@code null} for none
     */
    public SnowflakeIdBeanRegistrar(CosIdProperties cosIdProperties,
                                    MachineProperties machineProperties,
                                    SnowflakeIdProperties snowflakeIdProperties,
                                    InstanceId instanceId,
                                    IdGeneratorProvider idGeneratorProvider,
                                    GuardDistribute guardDistribute,
                                    ClockBackwardsSynchronizer clockBackwardsSynchronizer,
                                    ObjectProvider<CustomizeSnowflakeIdProperties> customizers,
                                    @Nullable BeanDefinitionRegistry beanDefinitions) {
        this.cosIdProperties = cosIdProperties;
        this.machineProperties = machineProperties;
        this.snowflakeIdProperties = snowflakeIdProperties;
        this.instanceId = instanceId;
        this.idGeneratorProvider = idGeneratorProvider;
        this.guardDistribute = guardDistribute;
        this.clockBackwardsSynchronizer = clockBackwardsSynchronizer;
        this.beanDefinitions = beanDefinitions;
        customizers.orderedStream().forEach(customize -> customize.customize(snowflakeIdProperties));
    }
    
    /**
     * The bean name of the snowflake id {@code name}.
     */
    public static String beanName(String name) {
        return name + "SnowflakeId";
    }
    
    /**
     * The snowflake ids of the properties: the share, when enabled, and the named ones.
     */
    public Map<String, SnowflakeIdProperties.IdDefinition> definitions() {
        Map<String, SnowflakeIdProperties.IdDefinition> definitions = new LinkedHashMap<>();
        if (snowflakeIdProperties.getShare().isEnabled()) {
            definitions.put(IdGeneratorProvider.SHARE, snowflakeIdProperties.getShare());
        }
        definitions.putAll(snowflakeIdProperties.getProvider());
        return definitions;
    }
    
    /**
     * The factory method of the {@code {name}SnowflakeId} beans.
     */
    public SnowflakeId createBean(String name) {
        SnowflakeIdProperties.IdDefinition idDefinition = definitions().get(name);
        if (idDefinition == null) {
            throw new IllegalStateException("Snowflake id [" + name + "] is not configured any more: a customizer removed it.");
        }
        return create(name, idDefinition);
    }
    
    /**
     * Snowflake ids a customizer added have no bean definition: they are only registered in the provider, here.
     */
    @Override
    public void afterPropertiesSet() {
        definitions().forEach((name, idDefinition) -> {
            if (beanDefinitions != null && beanDefinitions.containsBeanDefinition(beanName(name))) {
                return;
            }
            create(name, idDefinition);
            if (log.isInfoEnabled()) {
                log.info("Snowflake id [{}] was added by a customizer: registered in the IdGeneratorProvider only, not as a bean.", name);
            }
        });
    }
    
    /**
     * Remove the ids from the provider, where it still holds them, so a later context does not get ids of this one,
     * whose machine ids are released.
     */
    @Override
    public void destroy() {
        created.forEach((name, snowflakeId) -> {
            if (idGeneratorProvider.get(name).orElse(null) == snowflakeId) {
                idGeneratorProvider.remove(name);
            }
        });
    }
    
    /**
     * Create the snowflake id {@code name} and register it in the {@link IdGeneratorProvider} unless one is there.
     */
    public SnowflakeId create(String name, SnowflakeIdProperties.IdDefinition idDefinition) {
        IdConverterDefinition converterDefinition = idDefinition.getConverter();
        if (converterDefinition != null && converterDefinition.getGroupPrefix() != null && converterDefinition.getGroupPrefix().isEnabled()) {
            throw new IllegalStateException("Snowflake id [" + name + "] has converter.group-prefix enabled, but snowflake ids have no group: "
                + "turn the group prefix off, or use a grouped segment id.");
        }
        SnowflakeId idGenerator = createIdGen(name, idDefinition, clockBackwardsSynchronizer);
        if (idGeneratorProvider.get(name).isEmpty()) {
            idGeneratorProvider.set(name, idGenerator);
        }
        created.put(name, idGenerator);
        return idGenerator;
    }
    
    private SnowflakeId createIdGen(String name, SnowflakeIdProperties.IdDefinition idDefinition,
                                    ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        boolean second = SnowflakeIdProperties.IdDefinition.TimestampUnit.SECOND.equals(idDefinition.getTimestampUnit());
        long epoch = getEpoch(name, idDefinition, second);
        int machineBit = MoreObjects.firstNonNull(idDefinition.getMachineBit(), machineProperties.getMachineBit());
        String namespace = Namespaces.firstNotBlank(idDefinition.getNamespace(), cosIdProperties.getNamespace());
        int machineId = guardDistribute.distribute(namespace, machineBit, instanceId, machineProperties.getSafeGuardDuration()).getMachineId();
        
        SnowflakeId snowflakeId;
        if (second) {
            snowflakeId = new SecondSnowflakeId(epoch, idDefinition.getTimestampBit(), machineBit, idDefinition.getSequenceBit(), machineId, idDefinition.getSequenceResetThreshold());
        } else {
            snowflakeId =
                new MillisecondSnowflakeId(epoch, idDefinition.getTimestampBit(), machineBit, idDefinition.getSequenceBit(), machineId, idDefinition.getSequenceResetThreshold());
        }
        // The lease inside the clock sync: a generation retried after waiting for the clock checks the lease again.
        if (machineProperties.getGuarder().isFailFast()) {
            snowflakeId = new LeasedSnowflakeId(snowflakeId, guardDistribute.getLease(namespace, instanceId));
        }
        if (idDefinition.isClockSync()) {
            snowflakeId = new ClockSyncSnowflakeId(snowflakeId, clockBackwardsSynchronizer);
        }
        IdConverterDefinition converterDefinition = idDefinition.getConverter();
        final ZoneId zoneId = snowflakeIdProperties.getZoneId();
        return new SnowflakeIdConverterDecorator(snowflakeId, converterDefinition, zoneId).decorate();
    }
    
    /**
     * The epoch in the id's timestamp unit. The default epoch is in milliseconds, so second ids use
     * {@link CosId#COSID_EPOCH_SECOND} for it; any other epoch must not be after the current time in that unit, which
     * catches an epoch in milliseconds for second ids (it would make every id negative).
     */
    private long getEpoch(String name, SnowflakeIdProperties.IdDefinition idDefinition, boolean second) {
        long epoch = idDefinition.getEpoch() > 0 ? idDefinition.getEpoch() : snowflakeIdProperties.getEpoch();
        if (second && epoch == CosId.COSID_EPOCH) {
            return CosId.COSID_EPOCH_SECOND;
        }
        long now = second ? System.currentTimeMillis() / 1000 : System.currentTimeMillis();
        if (epoch > now) {
            throw new IllegalStateException("Snowflake id [" + name + "] has epoch [" + epoch + "] after the current time [" + now + "] in "
                + (second ? "seconds: timestamp-unit second needs an epoch in seconds." : "milliseconds."));
        }
        return epoch;
    }
    
}
