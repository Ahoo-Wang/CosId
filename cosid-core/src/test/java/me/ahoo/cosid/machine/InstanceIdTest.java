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

import me.ahoo.cosid.machine.InstanceId;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * InstanceIdTest .
 *
 * @author ahoo wang
 */
class InstanceIdTest {
    InstanceId instanceId1 = InstanceId.of("localhost", 8080, true);
    InstanceId instanceId2 = InstanceId.of("localhost", 8080, true);
    
    @Test
    void testEquals() {
        Assertions.assertEquals(instanceId1, instanceId2);
    }
    
    @Test
    void testHashCode() {
        Assertions.assertEquals(instanceId1.hashCode(), instanceId2.hashCode());
    }
}
