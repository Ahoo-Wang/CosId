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

package me.ahoo.cosid.snowflake;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.machine.MachineIdLostException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class LeasedSnowflakeIdTest {
    
    @Test
    void generateWhileLeaseIsValid() {
        MachineIdLease lease = new MachineIdLease("ns", InstanceId.NONE, Duration.ofMinutes(5));
        lease.renew(System.currentTimeMillis());
        MillisecondSnowflakeId actual = new MillisecondSnowflakeId(1);
        LeasedSnowflakeId snowflakeId = new LeasedSnowflakeId(actual, lease);
        
        Assertions.assertTrue(snowflakeId.generate() > 0);
        Assertions.assertSame(actual, snowflakeId.getActual());
        Assertions.assertSame(lease, snowflakeId.getLease());
        Assertions.assertEquals(actual.getEpoch(), snowflakeId.getEpoch());
        Assertions.assertEquals(actual.getTimestampBit(), snowflakeId.getTimestampBit());
        Assertions.assertEquals(actual.getMachineBit(), snowflakeId.getMachineBit());
        Assertions.assertEquals(actual.getSequenceBit(), snowflakeId.getSequenceBit());
        Assertions.assertEquals(actual.isSafeJavascript(), snowflakeId.isSafeJavascript());
        Assertions.assertEquals(actual.getMaxTimestamp(), snowflakeId.getMaxTimestamp());
        Assertions.assertEquals(actual.getMaxMachineId(), snowflakeId.getMaxMachineId());
        Assertions.assertEquals(actual.getMaxSequence(), snowflakeId.getMaxSequence());
        Assertions.assertEquals(actual.getLastTimestamp(), snowflakeId.getLastTimestamp());
        Assertions.assertEquals(actual.getLastTimestampAsMilliseconds(), snowflakeId.getLastTimestampAsMilliseconds());
        Assertions.assertEquals(1, snowflakeId.getMachineId());
        Assertions.assertNotNull(snowflakeId.stat());
    }
    
    @Test
    void generateShouldFailFastWhenLeaseIsLost() {
        MachineIdLease lease = new MachineIdLease("ns", InstanceId.NONE, Duration.ofMinutes(5));
        lease.renew(System.currentTimeMillis());
        lease.markLost();
        LeasedSnowflakeId snowflakeId = new LeasedSnowflakeId(new MillisecondSnowflakeId(1), lease);
        
        Assertions.assertThrows(MachineIdLostException.class, snowflakeId::generate);
    }
    
    @Test
    void generateShouldFailFastWhenLeaseExpired() {
        MachineIdLease lease = new MachineIdLease("ns", InstanceId.NONE, Duration.ofMinutes(5));
        lease.renew(System.currentTimeMillis() - Duration.ofMinutes(10).toMillis());
        LeasedSnowflakeId snowflakeId = new LeasedSnowflakeId(new MillisecondSnowflakeId(1), lease);
        
        Assertions.assertThrows(MachineIdLostException.class, snowflakeId::generate);
    }
}
