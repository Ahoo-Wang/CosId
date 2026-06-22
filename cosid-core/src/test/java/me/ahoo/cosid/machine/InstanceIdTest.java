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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InstanceIdTest {

    @Test
    void noneShouldUseStableFalseSentinelIdentity() {
        assertEquals("none", InstanceId.NONE.getInstanceId());
        assertFalse(InstanceId.NONE.isStable());
    }

    @Test
    void ofHostAndPortShouldFormatInstanceIdWithStability() {
        InstanceId instanceId = InstanceId.of("127.0.0.1", 8080, true);

        assertEquals("127.0.0.1:8080", instanceId.getInstanceId());
        assertTrue(instanceId.isStable());
    }

    @Test
    void equalityShouldIncludeInstanceTextAndStability() {
        InstanceId stable = InstanceId.of("instance-a", true);
        InstanceId same = InstanceId.of("instance-a", true);
        InstanceId dynamic = InstanceId.of("instance-a", false);
        InstanceId differentText = InstanceId.of("instance-b", true);

        assertEquals(stable, same);
        assertEquals(stable.hashCode(), same.hashCode());
        assertNotEquals(stable, dynamic);
        assertNotEquals(stable, differentText);
    }

    @Test
    void toStringShouldExposeInstanceIdAndStableFlag() {
        String text = InstanceId.of("instance-a", true).toString();

        assertTrue(text.contains("instanceId=instance-a"));
        assertTrue(text.contains("stable=true"));
    }
}
