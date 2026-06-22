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

package me.ahoo.cosid.example.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.example.redis.controller.IdController;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import org.junit.jupiter.api.Test;

/**
 * AppServerTest .
 *
 * @author ahoo wang
 */
class AppServerTest {
    
    @Test
    void idControllerGeneratesNamedIdsWithoutRedisServer() {
        IdGeneratorProvider provider = mock(IdGeneratorProvider.class);
        IdGenerator generator = mock(IdGenerator.class);
        when(provider.getRequired("biz_prefix_no")).thenReturn(generator);
        when(generator.generate()).thenReturn(2000000001L);
        when(generator.generateAsString()).thenReturn("BIZ2000000001");

        IdController controller = new IdController(provider);

        assertEquals(2000000001L, controller.generate("biz_prefix_no"));
        assertEquals("BIZ2000000001", controller.generateAsString("biz_prefix_no"));
    }
}
