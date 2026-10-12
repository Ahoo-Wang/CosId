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

import static org.junit.jupiter.api.Assertions.assertThrows;

import me.ahoo.cosid.CosId;
import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.machine.InstanceId;
import me.ahoo.cosid.machine.MachineIdLease;
import me.ahoo.cosid.machine.MachineIdLostException;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class LeasedInsideClockSyncTest {
    
    /**
     * The clock moves back, the generator waits for it, and the lease expires meanwhile: with the lease inside the
     * clock sync, the retried generation checks the lease again and refuses.
     */
    @Test
    void aGenerationRetriedAfterAClockSyncChecksTheLeaseAgain() {
        long[] times = {CosId.COSID_EPOCH + 1_000, CosId.COSID_EPOCH + 999, CosId.COSID_EPOCH + 1_001};
        int[] read = {0};
        MillisecondSnowflakeId actual = new MillisecondSnowflakeId(CosId.COSID_EPOCH, 41, 10, 12, 1, 100) {
            @Override
            protected long getCurrentTime() {
                return times[Math.min(read[0]++, times.length - 1)];
            }
        };
        MachineIdLease lease = new MachineIdLease("ns", InstanceId.of("holder", false), Duration.ofMinutes(5));
        lease.renew(System.currentTimeMillis());
        ClockBackwardsSynchronizer losingTheLeaseWhileWaiting = new ClockBackwardsSynchronizer() {
            @Override
            public void sync(long lastTimestamp) {
                lease.markLost();
            }
            
            @Override
            public void syncUninterruptibly(long lastTimestamp) {
                lease.markLost();
            }
        };
        SnowflakeId generator = new ClockSyncSnowflakeId(new LeasedSnowflakeId(actual, lease), losingTheLeaseWhileWaiting);
        generator.generate();
        
        assertThrows(MachineIdLostException.class, generator::generate);
    }
}
