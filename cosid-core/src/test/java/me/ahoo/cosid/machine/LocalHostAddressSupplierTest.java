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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import java.util.List;

class LocalHostAddressSupplierTest {

    @Test
    void getHostAddressShouldPreferLastEligibleNonLoopbackIpv4Candidate() {
        LocalHostAddressSupplier supplier = new LocalHostAddressSupplier(
            () -> "127.0.0.1",
            () -> List.of(
                candidate("::1", false, true, false),
                candidate("0.0.0.0", true, false, true),
                candidate("10.0.0.10", true, false, false),
                candidate("10.0.0.11", true, false, false)
            )
        );

        assertEquals("10.0.0.11", supplier.getHostAddress());
    }

    @Test
    void getHostAddressShouldUseFallbackWhenNetworkScanFails() {
        LocalHostAddressSupplier supplier = new LocalHostAddressSupplier(
            () -> "127.0.0.1",
            () -> {
                throw new IllegalStateException("network unavailable");
            }
        );

        assertEquals("127.0.0.1", supplier.getHostAddress());
    }

    @Test
    void instanceShouldReturnNonBlankAddressOnCurrentHost() {
        String hostAddress = LocalHostAddressSupplier.INSTANCE.getHostAddress();

        assertFalse(hostAddress == null || hostAddress.isBlank(), "host address should be non-blank");
    }

    private static LocalHostAddressSupplier.HostAddressCandidate candidate(
        String hostAddress, boolean ipv4, boolean loopback, boolean anyLocal) {
        return new LocalHostAddressSupplier.HostAddressCandidate(hostAddress, ipv4, loopback, anyLocal);
    }
}
