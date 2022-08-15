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

package me.ahoo.cosid.cosid;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

class RadixCosIdStateParserTest {
    private final Radix62CosIdGenerator radix62CosIdGenerator = new Radix62CosIdGenerator(1);
    
    @Test
    void generateAsString() {
        String id1 = radix62CosIdGenerator.generateAsString();
        String id2 = radix62CosIdGenerator.generateAsString();
        assertThat(id2, greaterThan(id1));
        assertThat(id2.length(), equalTo(id1.length()));
    }
    
    @Test
    void generateAsState() {
        CosIdState state1 = radix62CosIdGenerator.generateAsState();
        CosIdState state2 = radix62CosIdGenerator.generateAsState();
        assertThat(state2, greaterThan(state1));
    }
    
    @Test
    void asState() {
        CosIdState cosIdState = radix62CosIdGenerator.generateAsState();
        String tStr = Long.toString(cosIdState.getTimestamp(), 36);
        
        String idStr = radix62CosIdGenerator.getStateParser().asString(cosIdState);
        CosIdState cosIdState2 = radix62CosIdGenerator.getStateParser().asState(idStr);
        assertThat(cosIdState, equalTo(cosIdState2));
    }
    
    @Test
    void asString() {
        String id = radix62CosIdGenerator.generateAsString();
        CosIdState cosIdState = radix62CosIdGenerator.getStateParser().asState(id);
        String id2 = radix62CosIdGenerator.getStateParser().asString(cosIdState);
        assertThat(id, equalTo(id2));
    }
    
    @Test
    void asString36() {
        Radix36CosIdGenerator radix36CosIdGenerator = new Radix36CosIdGenerator(1);
        String id = radix36CosIdGenerator.generateAsString();
        CosIdState cosIdState = radix36CosIdGenerator.getStateParser().asState(id);
        String id2 = radix36CosIdGenerator.getStateParser().asString(cosIdState);
        assertThat(id, equalTo(id2));
    }
}
