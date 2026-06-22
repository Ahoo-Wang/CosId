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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NamespacedInstanceIdTest {

    @Test
    void shouldExposeNamespaceAndInstanceId() {
        InstanceId instanceId = InstanceId.of("instance-a", false);
        NamespacedInstanceId namespacedInstanceId = new NamespacedInstanceId("namespace", instanceId);

        assertEquals("namespace", namespacedInstanceId.getNamespace());
        assertEquals(instanceId, namespacedInstanceId.getInstanceId());
    }

    @Test
    void equalityShouldIncludeNamespaceAndFullInstanceIdentity() {
        NamespacedInstanceId left = new NamespacedInstanceId("namespace", InstanceId.of("instance-a", false));
        NamespacedInstanceId same = new NamespacedInstanceId("namespace", InstanceId.of("instance-a", false));
        NamespacedInstanceId differentNamespace = new NamespacedInstanceId("other", InstanceId.of("instance-a", false));
        NamespacedInstanceId differentStability = new NamespacedInstanceId("namespace", InstanceId.of("instance-a", true));

        assertEquals(left, same);
        assertEquals(left.hashCode(), same.hashCode());
        assertNotEquals(left, differentNamespace);
        assertNotEquals(left, differentStability);
    }

    @Test
    void toStringShouldExposeNamespaceAndInstanceId() {
        String text = new NamespacedInstanceId("namespace", InstanceId.of("instance-a", true)).toString();

        assertTrue(text.contains("namespace=namespace"));
        assertTrue(text.contains("instanceId=InstanceId{instanceId=instance-a, stable=true}"));
    }
}
