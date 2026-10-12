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

import java.time.Duration;

/**
 * Checks shared by the machine id lease store auto-configurations.
 */
final class MachineLeaseStores {
    private MachineLeaseStores() {
    }
    
    /**
     * The timeout of a lease store, which must be positive: the lease distributor holds a lock during each call.
     *
     * @throws IllegalStateException naming the property otherwise
     */
    static Duration positive(Duration timeout, String property) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("[" + property + "] must be positive: the machine id lease store holds a lock during each call. Was ["
                + timeout + "].");
        }
        return timeout;
    }
}
