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
import me.ahoo.cosid.cosid.FriendlyCosIdGenerator;
import me.ahoo.cosid.cosid.Radix36CosIdGenerator;
import me.ahoo.cosid.cosid.Radix62CosIdGenerator;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.GuardDistribute;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.Namespaces;
import me.ahoo.cosid.spring.boot.starter.machine.MachineProperties;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import jakarta.annotation.Nonnull;

/**
 * Auto-configuration class for CosId generators in Spring Boot applications.
 *
 * <p>This configuration automatically sets up CosId generators based on the provided properties.
 * It supports different types of CosId generators (Radix62, Radix36, Friendly) and integrates
 * with machine ID distribution and clock synchronization mechanisms.</p>
 *
 * <p>The configuration creates a primary {@link CosIdGenerator} bean that is registered with
 * the {@link IdGeneratorProvider} under the name "cosid". The generator is wrapped with
 * {@link ClockSyncCosIdGenerator} to handle clock synchronization issues.</p>
 *
 * <p>Example configuration in application.yml:
 * <pre>{@code
 * cosid:
 *   generator:
 *     enabled: true
 *     type: RADIX62
 *     namespace: myapp
 *     machine-bit: 10
 *     timestamp-bit: 41
 *     sequence-bit: 12
 * }</pre>
 *
 * @author ahoo wang
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdGeneratorEnabled
@EnableConfigurationProperties(CosIdGeneratorProperties.class)
public class CosIdGeneratorAutoConfiguration {
    private final CosIdProperties cosIdProperties;
    private final MachineProperties machineProperties;
    private final CosIdGeneratorProperties cosIdGeneratorProperties;

    /**
     * Constructs a new CosIdGeneratorAutoConfiguration with the required properties.
     *
     * @param cosIdProperties          the main CosId configuration properties
     * @param machineProperties        the machine-related configuration properties
     * @param cosIdGeneratorProperties the CosId generator specific properties
     */
    public CosIdGeneratorAutoConfiguration(CosIdProperties cosIdProperties, MachineProperties machineProperties, CosIdGeneratorProperties cosIdGeneratorProperties) {
        this.cosIdProperties = cosIdProperties;
        this.machineProperties = machineProperties;
        this.cosIdGeneratorProperties = cosIdGeneratorProperties;
    }

    /**
     * Creates and configures the primary CosId generator bean.
     *
     * <p>This method distributes a machine ID using the configured distributor, creates the appropriate
     * CosId generator based on the type specified in properties, wraps it with clock synchronization,
     * and registers it with the ID generator provider.</p>
     *
     * @param guardDistribute            the distributor responsible for allocating machine IDs
     * @param instanceId                 the unique identifier for this application instance
     * @param idGeneratorProvider        the provider where the generator will be registered
     * @param clockBackwardsSynchronizer the synchronizer to handle clock drift issues
     * @return a configured CosIdGenerator instance wrapped with clock synchronization
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public CosIdGenerator cosIdGenerator(GuardDistribute guardDistribute, final InstanceId instanceId, IdGeneratorProvider idGeneratorProvider,
                                         ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        String namespace = Namespaces.firstNotBlank(cosIdGeneratorProperties.getNamespace(), cosIdProperties.getNamespace());
        int machineId = guardDistribute.distribute(namespace, cosIdGeneratorProperties.getMachineBit(), instanceId, machineProperties.getSafeGuardDuration()).getMachineId();
        CosIdGenerator cosIdGenerator = createCosIdGenerator(machineId);

        CosIdGenerator clockSyncCosIdGenerator = new ClockSyncCosIdGenerator(cosIdGenerator, clockBackwardsSynchronizer);
        idGeneratorProvider.set(CosId.COSID, clockSyncCosIdGenerator);
        return clockSyncCosIdGenerator;
    }

    /**
     * Creates a CosId generator instance based on the configured type.
     *
     * <p>This method instantiates the appropriate CosId generator implementation
     * (Radix62, Radix36, or Friendly) using the configured bit allocations and machine ID.</p>
     *
     * @param machineId the machine ID to use for ID generation
     * @return a new CosIdGenerator instance of the configured type
     * @throws IllegalStateException if an unsupported generator type is configured
     */
    @Nonnull
    private CosIdGenerator createCosIdGenerator(int machineId) {
        switch (cosIdGeneratorProperties.getType()) {
            case RADIX62 -> {
                return new Radix62CosIdGenerator(cosIdGeneratorProperties.getTimestampBit(),
                    cosIdGeneratorProperties.getMachineBit(), cosIdGeneratorProperties.getSequenceBit(), machineId,
                    cosIdGeneratorProperties.getSequenceResetThreshold());
            }
            case RADIX36 -> {
                return new Radix36CosIdGenerator(cosIdGeneratorProperties.getTimestampBit(),
                    cosIdGeneratorProperties.getMachineBit(), cosIdGeneratorProperties.getSequenceBit(), machineId,
                    cosIdGeneratorProperties.getSequenceResetThreshold());
            }
            case FRIENDLY -> {
                return new FriendlyCosIdGenerator(cosIdGeneratorProperties.getTimestampBit(),
                    cosIdGeneratorProperties.getMachineBit(), cosIdGeneratorProperties.getSequenceBit(), machineId,
                    cosIdGeneratorProperties.getSequenceResetThreshold(), cosIdGeneratorProperties.getZoneId(), cosIdGeneratorProperties.isPadStart());
            }
            default -> throw new IllegalStateException("Unexpected value: " + cosIdGeneratorProperties.getType());
        }
    }
}
