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

package me.ahoo.cosid.example.zookeeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.example.zookeeper.controller.IdController;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import org.junit.jupiter.api.Test;

/**
 * AppServerTest .
 *
 * @author ahoo wang
 */
class AppServerTest {
    
    @Test
    void idControllerGeneratesFromShareProviderWithoutZookeeperServer() {
        IdGeneratorProvider provider = mock(IdGeneratorProvider.class);
        IdGenerator share = mock(IdGenerator.class);
        when(provider.getShare()).thenReturn(share);
        when(share.generate()).thenReturn(321L);
        when(share.generateAsString()).thenReturn("ID-321");

        IdController controller = new IdController(provider);

        assertEquals(321L, controller.generate());
        assertEquals("ID-321", controller.generateAsString());
    }
}
