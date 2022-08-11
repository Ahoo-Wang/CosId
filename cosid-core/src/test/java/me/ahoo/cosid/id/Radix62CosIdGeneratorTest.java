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

package me.ahoo.cosid.id;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

class Radix62CosIdGeneratorTest {
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
}
