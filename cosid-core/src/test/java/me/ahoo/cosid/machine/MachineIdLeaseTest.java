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

package me.ahoo.cosid.machine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class MachineIdLeaseTest {
    private static final InstanceId INSTANCE_ID = InstanceId.of("instance", false);
    
    @Test
    void foreverLeaseIsAlwaysValid() {
        Assertions.assertTrue(MachineIdLease.FOREVER.isValid(Long.MAX_VALUE));
        Assertions.assertDoesNotThrow(MachineIdLease.FOREVER::ensureValid);
    }
    
    @Test
    void newLeaseIsInvalidUntilRenewed() {
        MachineIdLease lease = new MachineIdLease("ns", INSTANCE_ID, Duration.ofMinutes(5));
        
        Assertions.assertFalse(lease.isValid(System.currentTimeMillis()));
        Assertions.assertThrows(MachineIdLostException.class, lease::ensureValid);
    }
    
    @Test
    void renewKeepsLeaseValidForSafeGuardDurationMinusMargin() {
        MachineIdLease lease = new MachineIdLease("ns", INSTANCE_ID, Duration.ofMinutes(5));
        long renewedAt = 1_000_000L;
        
        lease.renew(renewedAt);
        
        long validUntil = renewedAt + Duration.ofMinutes(5).toMillis() - MachineIdLease.MAX_CLOCK_SKEW_MARGIN.toMillis();
        Assertions.assertEquals(validUntil, lease.getValidUntil());
        Assertions.assertTrue(lease.isValid(validUntil));
        Assertions.assertFalse(lease.isValid(validUntil + 1));
    }
    
    @Test
    void marginIsAtMostTenPercentOfShortSafeGuardDuration() {
        Assertions.assertEquals(900, MachineIdLease.validityMillis(Duration.ofSeconds(1)));
    }
    
    @Test
    void renewNeverMovesBackwards() {
        MachineIdLease lease = new MachineIdLease("ns", INSTANCE_ID, Duration.ofMinutes(5));
        lease.renew(2_000_000L);
        long validUntil = lease.getValidUntil();
        
        lease.renew(1_000_000L);
        
        Assertions.assertEquals(validUntil, lease.getValidUntil());
    }
    
    @Test
    void lostLeaseNeverBecomesValidAgain() {
        MachineIdLease lease = new MachineIdLease("ns", INSTANCE_ID, Duration.ofMinutes(5));
        lease.renew(System.currentTimeMillis());
        
        lease.markLost();
        lease.renew(System.currentTimeMillis());
        
        Assertions.assertTrue(lease.isLost());
        MachineIdLostException exception = Assertions.assertThrows(MachineIdLostException.class, lease::ensureValid);
        Assertions.assertEquals("ns", exception.getNamespace());
        Assertions.assertEquals(INSTANCE_ID, exception.getInstanceId());
    }
}
