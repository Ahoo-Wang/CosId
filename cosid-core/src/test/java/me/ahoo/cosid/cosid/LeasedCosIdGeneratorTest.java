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

package me.ahoo.cosid.cosid;

import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.machine.MachineIdLostException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class LeasedCosIdGeneratorTest {
    
    @Test
    void generateWhileLeaseIsValid() {
        MachineIdLease lease = new MachineIdLease("ns", InstanceId.NONE, Duration.ofMinutes(5));
        lease.renew(System.currentTimeMillis());
        Radix62CosIdGenerator actual = new Radix62CosIdGenerator(1);
        LeasedCosIdGenerator generator = new LeasedCosIdGenerator(actual, lease);
        
        Assertions.assertNotNull(generator.generateAsString());
        Assertions.assertSame(actual, generator.getActual());
        Assertions.assertSame(lease, generator.getLease());
        Assertions.assertEquals(1, generator.getMachineId());
        Assertions.assertEquals(actual.getLastTimestamp(), generator.getLastTimestamp());
        Assertions.assertSame(actual.getStateParser(), generator.getStateParser());
        Assertions.assertNotNull(generator.stat());
    }
    
    @Test
    void generateShouldFailFastWhenLeaseIsLost() {
        MachineIdLease lease = new MachineIdLease("ns", InstanceId.NONE, Duration.ofMinutes(5));
        lease.markLost();
        LeasedCosIdGenerator generator = new LeasedCosIdGenerator(new Radix62CosIdGenerator(1), lease);
        
        Assertions.assertThrows(MachineIdLostException.class, generator::generateAsState);
    }
}
