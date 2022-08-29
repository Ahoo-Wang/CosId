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

@ConfigurationProperties(prefix = MachineProperties.PREFIX)
public class MachineProperties {
    public static final String PREFIX = CosId.COSID_PREFIX + "machine";
    private boolean enabled = false;
    private Boolean stable;
    
    private Integer port;
    private String instanceId;
    
    private int machineBit = MillisecondSnowflakeId.DEFAULT_MACHINE_BIT;
    
    private MachineProperties.StateStorage stateStorage;
    private MachineProperties.Distributor distributor;
    private MachineProperties.Guarder guarder;
    private MachineProperties.ClockBackwards clockBackwards;
    
    public MachineProperties() {
        stateStorage = new MachineProperties.StateStorage();
        distributor = new MachineProperties.Distributor();
        guarder = new MachineProperties.Guarder();
        clockBackwards = new MachineProperties.ClockBackwards();
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public Boolean getStable() {
        return stable;
    }
    
    public void setStable(Boolean stable) {
        this.stable = stable;
    }
    
    public Integer getPort() {
        return port;
    }
    
    public void setPort(Integer port) {
        this.port = port;
    }
    
    public String getInstanceId() {
        return instanceId;
    }
    
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
    
    public int getMachineBit() {
        return machineBit;
    }
    
    public void setMachineBit(int machineBit) {
        this.machineBit = machineBit;
    }
    
    public MachineProperties.StateStorage getStateStorage() {
        return stateStorage;
    }
    
    public void setStateStorage(MachineProperties.StateStorage stateStorage) {
        this.stateStorage = stateStorage;
    }
    
    public MachineProperties.Distributor getDistributor() {
        return distributor;
    }
    
    public void setDistributor(MachineProperties.Distributor distributor) {
        this.distributor = distributor;
    }
    
    public MachineProperties.Guarder getGuarder() {
        return guarder;
    }
    
    public MachineProperties setGuarder(MachineProperties.Guarder guarder) {
        this.guarder = guarder;
        return this;
    }
    
    public ClockBackwards getClockBackwards() {
        return clockBackwards;
    }
    
    public void setClockBackwards(ClockBackwards clockBackwards) {
        this.clockBackwards = clockBackwards;
    }
    
    public Duration getSafeGuardDuration() {
        MachineProperties.Guarder guarder = getGuarder();
        if (guarder.isEnabled()) {
            return guarder.getSafeGuardDuration();
        }
        return MachineIdDistributor.FOREVER_SAFE_GUARD_DURATION;
    }
    
    public static class StateStorage {
        
        private MachineProperties.StateStorage.Local local;
        
        public StateStorage() {
            this.local = new MachineProperties.StateStorage.Local();
        }
        
        public MachineProperties.StateStorage.Local getLocal() {
            return local;
        }
        
        public void setLocal(MachineProperties.StateStorage.Local local) {
            this.local = local;
        }
        
        public static class Local {
            
            private String stateLocation = LocalMachineStateStorage.DEFAULT_STATE_LOCATION_PATH;
            
            public String getStateLocation() {
                return stateLocation;
            }
            
            public void setStateLocation(String stateLocation) {
                this.stateLocation = stateLocation;
            }
        }

    }
    
    public static class Distributor {
        public static final String TYPE = PREFIX + ".distributor.type";
        private MachineProperties.Distributor.Type type = MachineProperties.Distributor.Type.MANUAL;
        
        private MachineProperties.Manual manual;
        private MachineProperties.Redis redis;
        
        public Distributor() {
            this.redis = new MachineProperties.Redis();
        }
        
        public MachineProperties.Distributor.Type getType() {
            return type;
        }
        
        public void setType(MachineProperties.Distributor.Type type) {
            this.type = type;
        }
        
        public MachineProperties.Manual getManual() {
            return manual;
        }
        
        public void setManual(MachineProperties.Manual manual) {
            this.manual = manual;
        }
        
        public MachineProperties.Redis getRedis() {
            return redis;
        }
        
        public void setRedis(MachineProperties.Redis redis) {
            this.redis = redis;
        }
        
        public enum Type {
            MANUAL,
            STATEFUL_SET,
            JDBC,
            REDIS,
            ZOOKEEPER,
            PROXY
        }
    }
    
    public static class Manual {
        
        private Integer machineId;
        
        public Integer getMachineId() {
            return machineId;
        }
        
        public void setMachineId(Integer machineId) {
            this.machineId = machineId;
        }
    }
    
    public static class Redis {
        
        private Duration timeout = Duration.ofSeconds(1);
        
        public Duration getTimeout() {
            return timeout;
        }
        
        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
    
    public static class Guarder {
        private boolean enabled = true;
        private Duration initialDelay = DefaultMachineIdGuarder.DEFAULT_INITIAL_DELAY;
        private Duration delay = DefaultMachineIdGuarder.DEFAULT_DELAY;
        
        private Duration safeGuardDuration = Duration.ofMinutes(5);
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public MachineProperties.Guarder setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Duration getInitialDelay() {
            return initialDelay;
        }
        
        public MachineProperties.Guarder setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
            return this;
        }
        
        public Duration getDelay() {
            return delay;
        }
        
        public MachineProperties.Guarder setDelay(Duration delay) {
            this.delay = delay;
            return this;
        }
        
        public Duration getSafeGuardDuration() {
            return safeGuardDuration;
        }
        
        public MachineProperties.Guarder setSafeGuardDuration(Duration safeGuardDuration) {
            this.safeGuardDuration = safeGuardDuration;
            return this;
        }
    }
    
    public static class ClockBackwards {
        
        private int spinThreshold = DefaultClockBackwardsSynchronizer.DEFAULT_SPIN_THRESHOLD;
        private int brokenThreshold = DefaultClockBackwardsSynchronizer.DEFAULT_BROKEN_THRESHOLD;
        
        public int getSpinThreshold() {
            return spinThreshold;
        }
        
        public void setSpinThreshold(int spinThreshold) {
            this.spinThreshold = spinThreshold;
        }
        
        public int getBrokenThreshold() {
            return brokenThreshold;
        }
        
        public void setBrokenThreshold(int brokenThreshold) {
            this.brokenThreshold = brokenThreshold;
        }
    }
}
