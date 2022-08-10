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

package me.ahoo.cosid.string;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

class CosIdIdStateParserTest {
    private final CosIdGenerator cosIdGenerator = new CosIdGenerator(1);
    
    @Test
    void generateAsString() {
        String id1 = cosIdGenerator.generateAsString();
        String id2 = cosIdGenerator.generateAsString();
        assertThat(id2, greaterThan(id1));
        assertThat(id2.length(), equalTo(id1.length()));
    }
    
    @Test
    void generateAsState() {
        CosIdState state1 = cosIdGenerator.generateAsState();
        CosIdState state2 = cosIdGenerator.generateAsState();
        assertThat(state2, greaterThan(state1));
    }
    
    @Test
    void asState() {
        CosIdState cosIdState = cosIdGenerator.generateAsState();
        String tStr = Long.toString(cosIdState.getTimestamp(), 36);
        
        String idStr = cosIdGenerator.getStateParser().asString(cosIdState);
        CosIdState cosIdState2 = cosIdGenerator.getStateParser().asState(idStr);
        assertThat(cosIdState, equalTo(cosIdState2));
    }
    
    @Test
    void asString() {
        String id = cosIdGenerator.generateAsString();
        CosIdState cosIdState = cosIdGenerator.getStateParser().asState(id);
        String id2 = cosIdGenerator.getStateParser().asString(cosIdState);
        assertThat(id, equalTo(id2));
    }
}
