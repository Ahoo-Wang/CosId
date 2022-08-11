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

package me.ahoo.cosid.spring.boot.starter.cosid;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.cosid.ClockSyncCosIdGenerator;
import me.ahoo.cosid.cosid.CosIdGenerator;
import me.ahoo.cosid.cosid.Radix62CosIdGenerator;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.MachineId;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * CosId Auto Configuration.
 *
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdGeneratorEnabled
@EnableConfigurationProperties(CosIdGeneratorProperties.class)
public class CosIdGeneratorAutoConfiguration {
    private final MachineProperties machineProperties;
    private final CosIdGeneratorProperties cosIdGeneratorProperties;
    
    public CosIdGeneratorAutoConfiguration(MachineProperties machineProperties, CosIdGeneratorProperties cosIdGeneratorProperties) {
        this.machineProperties = machineProperties;
        this.cosIdGeneratorProperties = cosIdGeneratorProperties;
    }
    
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public CosIdGenerator cosIdGenerator(final MachineId machineId, IdGeneratorProvider idGeneratorProvider,
                                         ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        Radix62CosIdGenerator cosIdGenerator =
            new Radix62CosIdGenerator(cosIdGeneratorProperties.getTimestampBit(),
                machineProperties.getMachineBit(), cosIdGeneratorProperties.getSequenceBit(), (int) machineId.getMachineId(),
                cosIdGeneratorProperties.getSequenceResetThreshold());
        
        CosIdGenerator clockSyncCosIdGenerator = new ClockSyncCosIdGenerator(cosIdGenerator, clockBackwardsSynchronizer);
        idGeneratorProvider.set(CosId.COSID, clockSyncCosIdGenerator);
        return clockSyncCosIdGenerator;
    }
}
