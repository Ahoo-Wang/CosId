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

package me.ahoo.cosid.example.cosid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.ahoo.cosid.cosid.CosIdGenerator;
import me.ahoo.cosid.cosid.CosIdIdStateParser;
import me.ahoo.cosid.cosid.CosIdState;
import me.ahoo.cosid.example.cosid.controller.IdController;
import me.ahoo.cosid.provider.IdGeneratorProvider;

import org.junit.jupiter.api.Test;

/**
 * AppServerTest .
 *
 * @author ahoo wang
 */
class AppServerTest {
    
    @Test
    void idControllerGeneratesAndParsesCosIdWithoutRedisServer() {
        IdGeneratorProvider provider = mock(IdGeneratorProvider.class);
        CosIdGenerator cosIdGenerator = mock(CosIdGenerator.class);
        CosIdIdStateParser stateParser = mock(CosIdIdStateParser.class);
        CosIdState state = new CosIdState(123456789L, 7, 9);
        when(cosIdGenerator.generateAsString()).thenReturn("COSID-123");
        when(cosIdGenerator.getStateParser()).thenReturn(stateParser);
        when(stateParser.asState("COSID-123")).thenReturn(state);

        IdController controller = new IdController(provider, cosIdGenerator);

        assertEquals("COSID-123", controller.generateAsString());
        assertEquals(state, controller.asState("COSID-123"));
    }
}
