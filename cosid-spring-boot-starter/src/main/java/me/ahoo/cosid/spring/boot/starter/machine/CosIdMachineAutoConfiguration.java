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

package me.ahoo.cosid.spring.boot.starter.machine;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.DefaultClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.DefaultMachineIdGuarder;
import me.ahoo.cosid.machine.GuardDistribute;
import me.ahoo.cosid.machine.HostAddressSupplier;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.LocalMachineStateStorage;
import me.ahoo.cosid.machine.MachineId;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.MachineIdGuarder;
import me.ahoo.cosid.machine.MachineStateStorage;
import me.ahoo.cosid.machine.ManualMachineIdDistributor;
import me.ahoo.cosid.machine.k8s.StatefulSetMachineIdDistributor;
import me.ahoo.cosid.machine.lease.LeaseStoreMachineIdDistributor;
import me.ahoo.cosid.spring.boot.starter.ConditionalOnCosIdEnabled;
import me.ahoo.cosid.spring.boot.starter.CosIdProperties;
import me.ahoo.cosid.spring.boot.starter.store.BuiltInStoreProvider;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnBuiltInMachineStore;
import me.ahoo.cosid.spring.boot.starter.store.ConditionalOnMachineStoreType;
import me.ahoo.cosid.util.ProcessId;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Objects;

/**
 * Auto-configuration for CosId machine ID management components.
 *
 * <p>This configuration class sets up the complete machine ID ecosystem including:
 * <ul>
 *   <li>Instance identification and machine ID distribution</li>
 *   <li>Machine state storage and persistence</li>
 *   <li>Clock synchronization and backwards handling</li>
 *   <li>Machine ID guarding and health monitoring</li>
 *   <li>Lifecycle management for startup/shutdown</li>
 * </ul>
 *
 * <p>The configuration supports multiple distribution strategies:
 * <ul>
 *   <li>Manual: Fixed machine ID assignment</li>
 *   <li>StatefulSet: Kubernetes StatefulSet-based distribution</li>
 *   <li>JDBC: Database-backed distribution</li>
 *   <li>Redis: Redis-backed distribution</li>
 *   <li>Zookeeper: Zookeeper-backed distribution</li>
 *   <li>Proxy: Remote proxy server distribution</li>
 *   <li>MongoDB: MongoDB-backed distribution</li>
 * </ul>
 *
 * <p>Example configuration:
 * <pre>{@code
 * cosid:
 *   machine:
 *     enabled: true
 *     distributor:
 *       type: manual
 *       manual:
 *         machine-id: 1
 * }</pre>
 */
@AutoConfiguration
@ConditionalOnCosIdEnabled
@ConditionalOnCosIdMachineEnabled
@EnableConfigurationProperties(MachineProperties.class)
@Slf4j
public class CosIdMachineAutoConfiguration {
    /**
     * Logged when a lease store runs without the guarder: its lease is never renewed.
     */
    static final String GUARDER_DISABLED_WARNING = "[" + MachineProperties.PREFIX + ".guarder.enabled=false] with a lease store: "
        + "this instance never renews its machine id lease, so any instance of the namespace that runs the guarder takes the "
        + "machine id over once its safe guard duration has passed, and both then generate the same ids. Disable the guarder "
        + "on every instance of the namespace, or on none.";
    private final CosIdProperties cosIdProperties;
    private final MachineProperties machineProperties;

    /**
     * Constructs a new machine auto-configuration with the provided properties.
     *
     * @param cosIdProperties   the main CosId configuration properties
     * @param machineProperties the machine-specific configuration properties
     */
    public CosIdMachineAutoConfiguration(CosIdProperties cosIdProperties, MachineProperties machineProperties) {
        this.cosIdProperties = cosIdProperties;
        this.machineProperties = machineProperties;
    }

    /**
     * Creates the instance identifier for this application instance.
     *
     * <p>The instance ID uniquely identifies this application instance within the
     * distributed system. It can be explicitly configured or automatically generated
     * from the host address and process ID.</p>
     *
     * <p>The stability flag indicates whether this instance has a stable network
     * identity that persists across restarts.</p>
     *
     * @param hostAddressSupplier supplier for the host address
     * @return the instance identifier
     */
    @Bean
    @ConditionalOnMissingBean
    public InstanceId instanceId(HostAddressSupplier hostAddressSupplier) {

        boolean stable = Boolean.TRUE.equals(machineProperties.getStable());

        if (!Strings.isNullOrEmpty(machineProperties.getInstanceId())) {
            return InstanceId.of(machineProperties.getInstanceId(), stable);
        }

        int port = ProcessId.CURRENT.getProcessId();
        if (Objects.nonNull(machineProperties.getPort()) && machineProperties.getPort() > 0) {
            port = machineProperties.getPort();
        }

        return InstanceId.of(hostAddressSupplier.getHostAddress(), port, stable);
    }

    /**
     * Creates the machine ID for this instance.
     *
     * <p>The machine ID is a unique identifier assigned to this instance within
     * the distributed system. It's distributed by the configured distributor
     * and used as part of snowflake ID generation.</p>
     *
     * @param guardDistribute the distributor responsible for assigning machine IDs
     * @param instanceId      the instance identifier
     * @return the assigned machine ID
     */
    @Bean
    @ConditionalOnMissingBean(value = MachineId.class)
    public MachineId machineId(GuardDistribute guardDistribute, InstanceId instanceId) {
        int machineId = guardDistribute.distribute(cosIdProperties.getNamespace(), machineProperties.getMachineBit(), instanceId, machineProperties.getSafeGuardDuration()).getMachineId();
        return new MachineId(machineId);
    }

    @Bean
    @ConditionalOnMissingBean
    public MachineStateStorage machineStateStorage() {
        if (Boolean.TRUE.equals(machineProperties.getStable())) {
            return new LocalMachineStateStorage(machineProperties.getStateStorage().getLocal().getStateLocation());
        }
        return MachineStateStorage.IN_MEMORY;
    }

    @Bean
    @ConditionalOnMissingBean
    public ClockBackwardsSynchronizer clockBackwardsSynchronizer() {
        MachineProperties.ClockBackwards clockBackwards = machineProperties.getClockBackwards();
        Preconditions.checkNotNull(clockBackwards, "cosid.machine.clockBackwards can not be null.");
        return new DefaultClockBackwardsSynchronizer(clockBackwards.getSpinThreshold(), clockBackwards.getBrokenThreshold());
    }

    @Bean
    @ConditionalOnMissingBean(MachineIdDistributor.class)
    @ConditionalOnMachineStoreType("manual")
    @ConditionalOnBuiltInMachineStore
    public ManualMachineIdDistributor machineIdDistributor(MachineStateStorage localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        MachineProperties.Manual manual = machineProperties.getDistributor().getManual();
        Preconditions.checkNotNull(manual, "cosid.machine.distributor.manual can not be null.");
        Integer machineId = manual.getMachineId();
        Preconditions.checkNotNull(machineId, "cosid.machine.distributor.manual.machineId can not be null.");
        Preconditions.checkArgument(machineId >= 0, "cosid.machine.distributor.manual.machineId can not be less than 0.");
        return new ManualMachineIdDistributor(machineId, localMachineState, clockBackwardsSynchronizer);
    }

    /**
     * The manual backend for {@link me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider} selection.
     */
    @Bean
    public BuiltInStoreProvider cosIdManualMachineStoreProvider() {
        return BuiltInStoreProvider.machine("manual", context -> machineIdDistributor(context.getMachineStateStorage(), context.getClockBackwardsSynchronizer()));
    }

    @Bean
    @ConditionalOnMissingBean(MachineIdDistributor.class)
    @ConditionalOnMachineStoreType("stateful_set")
    @ConditionalOnBuiltInMachineStore
    public StatefulSetMachineIdDistributor statefulSetMachineIdDistributor(MachineStateStorage localMachineState, ClockBackwardsSynchronizer clockBackwardsSynchronizer) {
        return new StatefulSetMachineIdDistributor(localMachineState, clockBackwardsSynchronizer);
    }

    /**
     * The Kubernetes StatefulSet backend for {@link me.ahoo.cosid.spring.boot.starter.store.CosIdStoreProvider} selection.
     */
    @Bean
    public BuiltInStoreProvider cosIdStatefulSetMachineStoreProvider() {
        return BuiltInStoreProvider.machine("stateful_set",
            context -> statefulSetMachineIdDistributor(context.getMachineStateStorage(), context.getClockBackwardsSynchronizer()));
    }

    /**
     * Creates the machine ID guarder for monitoring machine ID conflicts.
     *
     * <p>The guarder periodically checks for machine ID conflicts and maintains
     * the health status of machine ID distribution. If guarding is disabled,
     * returns a no-operation guarder.</p>
     *
     * @param machineIdDistributor the machine ID distributor to guard
     * @return the machine ID guarder instance
     */
    @Bean
    @ConditionalOnMissingBean
    public MachineIdGuarder machineIdGuarder(MachineIdDistributor machineIdDistributor) {
        MachineProperties.Guarder guarder = machineProperties.getGuarder();
        if (!guarder.isEnabled()) {
            if (machineIdDistributor instanceof LeaseStoreMachineIdDistributor) {
                log.warn(GUARDER_DISABLED_WARNING);
            }
            return MachineIdGuarder.NONE;
        }
        Duration proxyReadTimeout = cosIdProperties.getProxy().getReadTimeout();
        String timingProblem = GuarderTiming.check(guarder, GuarderTiming.storeTimeout(machineProperties.getDistributor(), proxyReadTimeout));
        if (timingProblem != null) {
            if (guarder.isFailFast()) {
                throw new IllegalStateException(timingProblem);
            }
            log.warn(timingProblem);
        }
        return new DefaultMachineIdGuarder(machineIdDistributor, DefaultMachineIdGuarder.executorService(), guarder.getInitialDelay(), guarder.getDelay(), machineProperties.getSafeGuardDuration(),
            guarder.getLocalStateInterval());
    }

    @Bean
    @ConditionalOnMissingBean
    public GuardDistribute guardDistribute(MachineIdDistributor machineIdDistributor, MachineIdGuarder machineIdGuarder) {
        return new CosIdGuardDistribute(machineIdDistributor, machineIdGuarder);
    }


    @Bean
    @ConditionalOnMissingBean
    public CosIdMachineIdLifecycle cosIdMachineIdLifecycle(MachineIdGuarder machineIdGuarder, MachineIdDistributor machineIdDistributor,
                                                           GuardDistribute guardDistribute) {
        return new CosIdMachineIdLifecycle(machineIdGuarder, machineIdDistributor, guardDistribute);
    }

    /**
     * The health indicator, only when the health support of Spring Boot Actuator is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    static class Health {
        /**
         * Creates the health indicator for machine ID monitoring.
         *
         * <p>This Spring Boot Actuator health indicator reports the status of machine
         * ID distribution and guarding. It integrates with the health check endpoints
         * to provide operational visibility.</p>
         *
         * @param machineIdGuarder the guarder to monitor for health status
         * @return the machine ID health indicator
         */
        @Bean
        @ConditionalOnMissingBean
        public MachineIdHealthIndicator machineIdHealthIndicator(MachineIdGuarder machineIdGuarder) {
            return new MachineIdHealthIndicator(machineIdGuarder);
        }
    }
}
