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

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.machine.DefaultClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.DefaultMachineIdGuarder;
import me.ahoo.cosid.machine.LocalMachineStateStorage;
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for machine ID management in distributed systems.
 *
 * <p>This class defines properties for configuring machine ID distribution, state storage,
 * guarding mechanisms, and clock synchronization. These properties control how
 * unique machine IDs are allocated and maintained across different instances in a cluster.</p>
 *
 * <p>The configuration supports multiple distribution strategies:
 * <ul>
 *   <li>MANUAL - Explicitly configured machine ID</li>
 *   <li>STATEFUL_SET - Kubernetes StatefulSet-based allocation</li>
 *   <li>JDBC - Database-based distribution</li>
 *   <li>MONGO - MongoDB-based distribution</li>
 *   <li>REDIS - Redis-based distribution</li>
 *   <li>ZOOKEEPER - ZooKeeper-based distribution</li>
 *   <li>PROXY - Proxy service-based distribution</li>
 * </ul>
 *
 * <p>Example configuration:
 * <pre>{@code
 * cosid:
 *   machine:
 *     enabled: true
 *     stable: true
 *     instance-id: my-instance-01
 *     distributor:
 *       type: REDIS
 *       redis:
 *         timeout: 2s
 *     guarder:
 *       enabled: true
 *       safe-guard-duration: 10m
 * }</pre>
 *
 * @author ahoo wang
 */
@ConfigurationProperties(prefix = MachineProperties.PREFIX)
public class MachineProperties {
    /**
     * The configuration property prefix for machine properties.
     */
    public static final String PREFIX = CosId.COSID_PREFIX + "machine";

    /**
     * Whether machine ID management is enabled.
     * Default is false.
     */
    private boolean enabled = false;

    /**
     * Whether the machine ID should be stable across restarts.
     * If null, determined automatically based on distributor type.
     */
    private Boolean stable;

    /**
     * The port number for this instance (used for instance identification).
     */
    private Integer port;

    /**
     * Unique identifier for this application instance.
     * Used to distinguish between multiple instances on the same machine.
     */
    private String instanceId;

    /**
     * Number of bits allocated for machine ID in generated IDs.
     * Default is {@link MillisecondSnowflakeId#DEFAULT_MACHINE_BIT}.
     */
    private int machineBit = MillisecondSnowflakeId.DEFAULT_MACHINE_BIT;

    /**
     * Configuration for machine state storage.
     */
    private MachineProperties.StateStorage stateStorage;

    /**
     * Configuration for machine ID distribution strategy.
     */
    private MachineProperties.Distributor distributor;

    /**
     * Configuration for machine ID guarding mechanisms.
     */
    private MachineProperties.Guarder guarder;

    /**
     * Configuration for clock backwards synchronization.
     */
    private MachineProperties.ClockBackwards clockBackwards;
    
    /**
     * Constructs a new MachineProperties instance with default configurations.
     *
     * <p>Initializes all nested configuration objects with their default values.</p>
     */
    public MachineProperties() {
        stateStorage = new MachineProperties.StateStorage();
        distributor = new MachineProperties.Distributor();
        guarder = new MachineProperties.Guarder();
        clockBackwards = new MachineProperties.ClockBackwards();
    }
    
    /**
     * Checks if machine ID management is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether machine ID management should be enabled.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets whether the machine ID should be stable across restarts.
     *
     * @return true if stable, false if not, null if auto-determined
     */
    public Boolean getStable() {
        return stable;
    }

    /**
     * Sets whether the machine ID should be stable across restarts.
     *
     * @param stable true for stable, false for dynamic, null for auto-determination
     */
    public void setStable(Boolean stable) {
        this.stable = stable;
    }

    /**
     * Gets the port number for this instance.
     *
     * @return the port number, or null if not set
     */
    public Integer getPort() {
        return port;
    }

    /**
     * Sets the port number for this instance.
     *
     * @param port the port number to set
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    /**
     * Gets the unique instance identifier.
     *
     * @return the instance ID string, or null if not set
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Sets the unique instance identifier.
     *
     * @param instanceId the instance ID to set
     */
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    /**
     * Gets the number of bits allocated for machine ID.
     *
     * @return the machine bit count
     */
    public int getMachineBit() {
        return machineBit;
    }

    /**
     * Sets the number of bits allocated for machine ID.
     *
     * @param machineBit the machine bit count to set
     */
    public void setMachineBit(int machineBit) {
        this.machineBit = machineBit;
    }
    
    /**
     * Gets the state storage configuration.
     *
     * @return the state storage configuration
     */
    public MachineProperties.StateStorage getStateStorage() {
        return stateStorage;
    }

    /**
     * Sets the state storage configuration.
     *
     * @param stateStorage the state storage configuration to set
     */
    public void setStateStorage(MachineProperties.StateStorage stateStorage) {
        this.stateStorage = stateStorage;
    }

    /**
     * Gets the distributor configuration.
     *
     * @return the distributor configuration
     */
    public MachineProperties.Distributor getDistributor() {
        return distributor;
    }

    /**
     * Sets the distributor configuration.
     *
     * @param distributor the distributor configuration to set
     */
    public void setDistributor(MachineProperties.Distributor distributor) {
        this.distributor = distributor;
    }

    /**
     * Gets the guarder configuration.
     *
     * @return the guarder configuration
     */
    public MachineProperties.Guarder getGuarder() {
        return guarder;
    }

    /**
     * Sets the guarder configuration.
     *
     * @param guarder the guarder configuration to set
     * @return this properties instance for method chaining
     */
    public MachineProperties setGuarder(MachineProperties.Guarder guarder) {
        this.guarder = guarder;
        return this;
    }

    /**
     * Gets the clock backwards configuration.
     *
     * @return the clock backwards configuration
     */
    public ClockBackwards getClockBackwards() {
        return clockBackwards;
    }

    /**
     * Sets the clock backwards configuration.
     *
     * @param clockBackwards the clock backwards configuration to set
     */
    public void setClockBackwards(ClockBackwards clockBackwards) {
        this.clockBackwards = clockBackwards;
    }

    /**
     * Gets the effective safe guard duration based on guarder configuration.
     *
     * <p>If the guarder is enabled, returns the configured safe guard duration.
     * Otherwise, returns {@link MachineIdDistributor#FOREVER_SAFE_GUARD_DURATION}.</p>
     *
     * @return the effective safe guard duration
     */
    public Duration getSafeGuardDuration() {
        MachineProperties.Guarder guarder = getGuarder();
        if (guarder.isEnabled()) {
            return guarder.getSafeGuardDuration();
        }
        return MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION;
    }
    
    /**
     * Configuration for machine state storage mechanisms.
     *
     * <p>This class configures how machine state (like allocated machine IDs)
     * is persisted across application restarts.</p>
     */
    public static class StateStorage {

        /**
         * Local file system storage configuration.
         */
        private MachineProperties.StateStorage.Local local;
        
        /**
         * Constructs a new StateStorage configuration with default local storage.
         */
        public StateStorage() {
            this.local = new MachineProperties.StateStorage.Local();
        }

        /**
         * Gets the local storage configuration.
         *
         * @return the local storage configuration
         */
        public MachineProperties.StateStorage.Local getLocal() {
            return local;
        }

        /**
         * Sets the local storage configuration.
         *
         * @param local the local storage configuration to set
         */
        public void setLocal(MachineProperties.StateStorage.Local local) {
            this.local = local;
        }
        
        /**
         * Configuration for local file system state storage.
         */
        public static class Local {

            /**
             * The file system path where machine state is stored.
             * Default is {@link LocalMachineStateStorage#DEFAULT_STATE_LOCATION_PATH}.
             */
            private String stateLocation = LocalMachineStateStorage.DEFAULT_STATE_LOCATION_PATH;
            
            /**
             * Gets the file system path for state storage.
             *
             * @return the state location path
             */
            public String getStateLocation() {
                return stateLocation;
            }

            /**
             * Sets the file system path for state storage.
             *
             * @param stateLocation the state location path to set
             */
            public void setStateLocation(String stateLocation) {
                this.stateLocation = stateLocation;
            }
        }
        
    }
    
    /**
     * Configuration for machine ID distribution strategies.
     *
     * <p>This class configures how machine IDs are allocated and distributed
     * across multiple instances in a distributed system. Different strategies
     * are available for different deployment scenarios.</p>
     */
    public static class Distributor {
        /**
         * The configuration property key for distributor type.
         */
        public static final String TYPE = PREFIX + ".distributor.type";

        /**
         * The type of distributor to use.
         * Default is MANUAL.
         */
        private MachineProperties.Distributor.Type type = MachineProperties.Distributor.Type.MANUAL;

        /**
         * Configuration for manual machine ID assignment.
         */
        private MachineProperties.Manual manual;

        /**
         * Configuration for Redis-based distribution.
         */
        private MachineProperties.Redis redis;

        /**
         * Configuration for MongoDB-based distribution.
         */
        private MachineProperties.Mongo mongo;
        
        /**
         * Constructs a new Distributor configuration with default Redis and Mongo settings.
         */
        public Distributor() {
            this.redis = new MachineProperties.Redis();
            this.mongo = new MachineProperties.Mongo();
        }

        /**
         * Gets the distributor type.
         *
         * @return the distributor type
         */
        public MachineProperties.Distributor.Type getType() {
            return type;
        }

        /**
         * Sets the distributor type.
         *
         * @param type the distributor type to set
         */
        public void setType(MachineProperties.Distributor.Type type) {
            this.type = type;
        }

        /**
         * Gets the manual configuration.
         *
         * @return the manual configuration, or null if not set
         */
        public MachineProperties.Manual getManual() {
            return manual;
        }

        /**
         * Sets the manual configuration.
         *
         * @param manual the manual configuration to set
         */
        public void setManual(MachineProperties.Manual manual) {
            this.manual = manual;
        }

        /**
         * Gets the Redis configuration.
         *
         * @return the Redis configuration
         */
        public MachineProperties.Redis getRedis() {
            return redis;
        }

        /**
         * Sets the Redis configuration.
         *
         * @param redis the Redis configuration to set
         */
        public void setRedis(MachineProperties.Redis redis) {
            this.redis = redis;
        }

        /**
         * Gets the MongoDB configuration.
         *
         * @return the MongoDB configuration
         */
        public Mongo getMongo() {
            return mongo;
        }

        /**
         * Sets the MongoDB configuration.
         *
         * @param mongo the MongoDB configuration to set
         */
        public void setMongo(Mongo mongo) {
            this.mongo = mongo;
        }
        
        /**
         * Enumeration of supported machine ID distributor types.
         */
        public enum Type {
            /**
             * Manual assignment of machine IDs.
             * Requires explicit configuration of machine IDs.
             */
            MANUAL,

            /**
             * Distribution based on Kubernetes StatefulSet ordinal.
             * Uses StatefulSet pod index for machine ID assignment.
             */
            STATEFUL_SET,

            /**
             * Database-based distribution using JDBC.
             * Stores machine ID assignments in a relational database.
             */
            JDBC,

            /**
             * MongoDB-based distribution.
             * Uses MongoDB for coordinating machine ID assignments.
             */
            MONGO,

            /**
             * Redis-based distribution.
             * Uses Redis for atomic machine ID allocation.
             */
            REDIS,

            /**
             * ZooKeeper-based distribution.
             * Uses ZooKeeper for distributed coordination.
             */
            ZOOKEEPER,

            /**
             * Proxy service-based distribution.
             * Delegates machine ID allocation to a proxy service.
             */
            PROXY
        }
    }
    
    /**
     * Configuration for manual machine ID assignment.
     *
     * <p>When using manual distribution, the machine ID must be explicitly
     * configured and must be unique across all instances.</p>
     */
    public static class Manual {

        /**
         * The manually assigned machine ID.
         * Must be unique across all instances in the cluster.
         */
        private Integer machineId;
        
        /**
         * Gets the manually assigned machine ID.
         *
         * @return the machine ID, or null if not set
         */
        public Integer getMachineId() {
            return machineId;
        }

        /**
         * Sets the manually assigned machine ID.
         *
         * @param machineId the machine ID to set
         */
        public void setMachineId(Integer machineId) {
            this.machineId = machineId;
        }
    }
    
    /**
     * Configuration for Redis-based machine ID distribution.
     *
     * <p>This configuration controls the timeout for Redis operations
     * used in machine ID allocation and coordination.</p>
     */
    public static class Redis {

        /**
         * Timeout for Redis operations.
         * Default is 1 second.
         */
        private Duration timeout = Duration.ofSeconds(1);
        
        /**
         * Gets the Redis operation timeout.
         *
         * @return the timeout duration
         */
        public Duration getTimeout() {
            return timeout;
        }

        /**
         * Sets the Redis operation timeout.
         *
         * @param timeout the timeout duration to set
         */
        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
    
    /**
     * Configuration for MongoDB-based machine ID distribution.
     *
     * <p>This configuration specifies the MongoDB database used for
     * storing machine ID assignments and coordination data.</p>
     */
    public static class Mongo {
        /**
         * The MongoDB database name for machine ID coordination.
         * Default is "cosid_db".
         */
        private String database = "cosid_db";
        
        /**
         * Gets the MongoDB database name.
         *
         * @return the database name
         */
        public String getDatabase() {
            return database;
        }

        /**
         * Sets the MongoDB database name.
         *
         * @param database the database name to set
         */
        public void setDatabase(String database) {
            this.database = database;
        }
    }
    
    /**
     * Configuration for machine ID guarding mechanisms.
     *
     * <p>The guarder monitors machine ID health and prevents conflicts
     * by periodically validating that the assigned machine ID is still valid.
     * If a conflict is detected, the application can take corrective action.</p>
     */
    public static class Guarder {
        /**
         * Whether machine ID guarding is enabled.
         * Default is true.
         */
        private boolean enabled = true;

        /**
         * Initial delay before starting the guarding task.
         * Default is {@link DefaultMachineIdGuarder#DEFAULT_INITIAL_DELAY}.
         */
        private Duration initialDelay = DefaultMachineIdGuarder.DEFAULT_INITIAL_DELAY;

        /**
         * Delay between guarding checks.
         * Default is {@link DefaultMachineIdGuarder#DEFAULT_DELAY}.
         */
        private Duration delay = DefaultMachineIdGuarder.DEFAULT_DELAY;

        /**
         * Duration for which the machine ID is considered safely guarded.
         * Default is 5 minutes.
         */
        private Duration safeGuardDuration = Duration.ofMinutes(5);
        
        /**
         * Checks if machine ID guarding is enabled.
         *
         * @return true if enabled, false otherwise
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether machine ID guarding should be enabled.
         *
         * @param enabled true to enable guarding, false to disable
         * @return this guarder configuration for method chaining
         */
        public MachineProperties.Guarder setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Gets the initial delay before starting guarding.
         *
         * @return the initial delay duration
         */
        public Duration getInitialDelay() {
            return initialDelay;
        }

        /**
         * Sets the initial delay before starting guarding.
         *
         * @param initialDelay the initial delay duration to set
         * @return this guarder configuration for method chaining
         */
        public MachineProperties.Guarder setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }

        /**
         * Gets the delay between guarding checks.
         *
         * @return the delay duration
         */
        public Duration getDelay() {
            return delay;
        }

        /**
         * Sets the delay between guarding checks.
         *
         * @param delay the delay duration to set
         * @return this guarder configuration for method chaining
         */
        public MachineProperties.Guarder setDelay(Duration delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Gets the safe guard duration.
         *
         * @return the safe guard duration
         */
        public Duration getSafeGuardDuration() {
            return safeGuardDuration;
        }

        /**
         * Sets the safe guard duration.
         *
         * @param safeGuardDuration the safe guard duration to set
         * @return this guarder configuration for method chaining
         */
        public MachineProperties.Guarder setSafeGuardDuration(Duration safeGuardDuration) {
            this.safeGuardDuration = safeGuardDuration;
            return this;
        }
    }
    
    /**
     * Configuration for clock backwards synchronization.
     *
     * <p>This configuration controls how the system handles clock drift
     * and backwards time adjustments, which can cause ID generation issues
     * in distributed systems.</p>
     */
    public static class ClockBackwards {

        /**
         * Threshold for spin-based synchronization attempts.
         * Default is {@link DefaultClockBackwardsSynchronizer#DEFAULT_SPIN_THRESHOLD}.
         */
        private int spinThreshold = DefaultClockBackwardsSynchronizer.DEFAULT_SPIN_THRESHOLD;

        /**
         * Threshold for considering the clock as broken.
         * Default is {@link DefaultClockBackwardsSynchronizer#DEFAULT_BROKEN_THRESHOLD}.
         */
        private int brokenThreshold = DefaultClockBackwardsSynchronizer.DEFAULT_BROKEN_THRESHOLD;
        
        /**
         * Gets the spin threshold for clock synchronization.
         *
         * @return the spin threshold value
         */
        public int getSpinThreshold() {
            return spinThreshold;
        }

        /**
         * Sets the spin threshold for clock synchronization.
         *
         * @param spinThreshold the spin threshold to set
         */
        public void setSpinThreshold(int spinThreshold) {
            this.spinThreshold = spinThreshold;
        }

        /**
         * Gets the broken threshold for clock validation.
         *
         * @return the broken threshold value
         */
        public int getBrokenThreshold() {
            return brokenThreshold;
        }

        /**
         * Sets the broken threshold for clock validation.
         *
         * @param brokenThreshold the broken threshold to set
         */
        public void setBrokenThreshold(int brokenThreshold) {
            this.brokenThreshold = brokenThreshold;
        }
    }
}
