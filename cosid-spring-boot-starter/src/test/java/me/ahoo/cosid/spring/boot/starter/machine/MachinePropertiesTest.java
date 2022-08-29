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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.machine.DefaultClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.LocalMachineStateStorage;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.test.MockIdGenerator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

class MachinePropertiesTest {
    
    @Test
    void isEnabled() {
        MachineProperties properties = new MachineProperties();
        assertThat(properties.isEnabled(), equalTo(false));
    }
    
    @Test
    void getClockBackwards() {
        MachineProperties properties = new MachineProperties();
        assertThat(properties.getClockBackwards(), notNullValue());
    }
    
    @Test
    void setClockBackwards() {
        MachineProperties.ClockBackwards clockBackwards = new MachineProperties.ClockBackwards();
        MachineProperties properties = new MachineProperties();
        properties.setClockBackwards(clockBackwards);
        Assertions.assertEquals(clockBackwards, properties.getClockBackwards());
    }
    
    @Test
    public void getStable() {
        MachineProperties properties = new MachineProperties();
        assertThat(properties.getStable(), nullValue());
    }
    
    @Test
    public void setStable() {
        boolean stable = true;
        MachineProperties properties = new MachineProperties();
        properties.setStable(stable);
        assertThat(properties.getStable(), equalTo(Boolean.TRUE));
    }
    
    @Test
    public void getPort() {
        MachineProperties properties = new MachineProperties();
        assertThat(properties.getPort(), nullValue());
    }
    
    @Test
    public void setPort() {
        int port = ThreadLocalRandom.current().nextInt();
        MachineProperties properties = new MachineProperties();
        properties.setPort(port);
        assertThat(properties.getPort(), equalToObject(port));
    }
    
    @Test
    public void getInstanceId() {
        MachineProperties properties = new MachineProperties();
        Assertions.assertNull(properties.getInstanceId());
    }
    
    @Test
    public void setInstanceId() {
        String instanceId = MockIdGenerator.INSTANCE.generateAsString();
        MachineProperties properties = new MachineProperties();
        properties.setInstanceId(instanceId);
        Assertions.assertEquals(instanceId, properties.getInstanceId());
    }
    
    @Test
    public void getMachineBit() {
        MachineProperties properties = new MachineProperties();
        Assertions.assertEquals(MillisecondSnowflakeId.DEFAULT_MACHINE_BIT, properties.getMachineBit());
    }
    
    @Test
    public void setMachineBit() {
        int machineBit = 9;
        MachineProperties properties = new MachineProperties();
        properties.setMachineBit(machineBit);
        Assertions.assertEquals(machineBit, properties.getMachineBit());
    }
    
    @Test
    public void getStateStorage() {
        MachineProperties properties = new MachineProperties();
        Assertions.assertNotNull(properties.getStateStorage());
    }
    
    @Test
    public void setStateStorage() {
        MachineProperties.StateStorage stateStorage = new MachineProperties.StateStorage();
        MachineProperties properties = new MachineProperties();
        properties.setStateStorage(stateStorage);
        Assertions.assertEquals(stateStorage, properties.getStateStorage());
    }
    
    @Test
    public void getDistributor() {
        MachineProperties properties = new MachineProperties();
        Assertions.assertNotNull(properties.getDistributor());
    }
    
    @Test
    public void setDistributor() {
        MachineProperties.Distributor distributor = new MachineProperties.Distributor();
        MachineProperties properties = new MachineProperties();
        properties.setDistributor(distributor);
        Assertions.assertEquals(distributor, properties.getDistributor());
    }
    
    public static class ClockBackwardsTest {
        @Test
        public void getSpinThreshold() {
            MachineProperties.ClockBackwards clockBackwards = new MachineProperties.ClockBackwards();
            Assertions.assertEquals(DefaultClockBackwardsSynchronizer.DEFAULT_SPIN_THRESHOLD, clockBackwards.getSpinThreshold());
        }
        
        @Test
        public void setSpinThreshold() {
            int spinThreshold = 100;
            MachineProperties.ClockBackwards clockBackwards = new MachineProperties.ClockBackwards();
            clockBackwards.setSpinThreshold(spinThreshold);
            Assertions.assertEquals(spinThreshold, clockBackwards.getSpinThreshold());
        }
        
        @Test
        public void getBrokenThreshold() {
            MachineProperties.ClockBackwards clockBackwards = new MachineProperties.ClockBackwards();
            Assertions.assertEquals(DefaultClockBackwardsSynchronizer.DEFAULT_BROKEN_THRESHOLD, clockBackwards.getBrokenThreshold());
        }
        
        @Test
        public void setBrokenThreshold() {
            int brokenThreshold = 10;
            MachineProperties.ClockBackwards clockBackwards = new MachineProperties.ClockBackwards();
            clockBackwards.setBrokenThreshold(brokenThreshold);
            Assertions.assertEquals(brokenThreshold, clockBackwards.getBrokenThreshold());
        }
    }
    
    public static class StateStorageTest {
        
        @Test
        public void getLocal() {
            MachineProperties.StateStorage stateStorage = new MachineProperties.StateStorage();
            Assertions.assertNotNull(stateStorage.getLocal());
        }
        
        @Test
        public void setLocal() {
            MachineProperties.StateStorage.Local local = new MachineProperties.StateStorage.Local();
            MachineProperties.StateStorage stateStorage = new MachineProperties.StateStorage();
            stateStorage.setLocal(local);
            Assertions.assertEquals(local, stateStorage.getLocal());
        }
    }
    
    public static class LocalTest {
        @Test
        public void getStateLocation() {
            MachineProperties.StateStorage.Local local = new MachineProperties.StateStorage.Local();
            Assertions.assertEquals(LocalMachineStateStorage.DEFAULT_STATE_LOCATION_PATH, local.getStateLocation());
        }
        
        @Test
        public void setStateLocation() {
            String stateLocation = MockIdGenerator.INSTANCE.generateAsString();
            MachineProperties.StateStorage.Local local = new MachineProperties.StateStorage.Local();
            local.setStateLocation(stateLocation);
            Assertions.assertEquals(stateLocation, local.getStateLocation());
        }
    }
    
    public static class DistributorTest {
        @Test
        public void getType() {
            MachineProperties.Distributor distributor = new MachineProperties.Distributor();
            Assertions.assertEquals(MachineProperties.Distributor.Type.MANUAL, distributor.getType());
        }
        
        @Test
        public void setType() {
            MachineProperties.Distributor.Type type = MachineProperties.Distributor.Type.JDBC;
            MachineProperties.Distributor distributor = new MachineProperties.Distributor();
            distributor.setType(type);
            Assertions.assertEquals(type, distributor.getType());
        }
        
        @Test
        public void getManual() {
            MachineProperties.Distributor distributor = new MachineProperties.Distributor();
            Assertions.assertNull(distributor.getManual());
        }
        
        @Test
        public void setManual() {
            MachineProperties.Manual manual = new MachineProperties.Manual();
            MachineProperties.Distributor distributor = new MachineProperties.Distributor();
            distributor.setManual(manual);
            Assertions.assertEquals(manual, distributor.getManual());
        }
        
        @Test
        public void getRedis() {
            MachineProperties.Distributor distributor = new MachineProperties.Distributor();
            Assertions.assertNotNull(distributor.getRedis());
        }
        
        @Test
        public void setRedis() {
            MachineProperties.Redis redis = new MachineProperties.Redis();
            MachineProperties.Distributor distributor = new MachineProperties.Distributor();
            distributor.setRedis(redis);
            Assertions.assertEquals(redis, distributor.getRedis());
        }
    }
    
    public static class ManualTest {
        
        @Test
        public void getMachineId() {
            MachineProperties.Manual manual = new MachineProperties.Manual();
            Assertions.assertNull(manual.getMachineId());
        }
        
        @Test
        public void setMachineId() {
            Integer machineId = 1;
            MachineProperties.Manual manual = new MachineProperties.Manual();
            manual.setMachineId(machineId);
            Assertions.assertEquals(machineId, manual.getMachineId());
        }
    }
    
    public static class RedisTest {
        @Test
        public void getTimeout() {
            MachineProperties.Redis redis = new MachineProperties.Redis();
            Assertions.assertEquals(Duration.ofSeconds(1), redis.getTimeout());
        }
        
        @Test
        public void setTimeout() {
            Duration timeout = Duration.ofSeconds(2);
            MachineProperties.Redis redis = new MachineProperties.Redis();
            redis.setTimeout(timeout);
            Assertions.assertEquals(timeout, redis.getTimeout());
        }
    }
}
