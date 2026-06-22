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

import static me.ahoo.cosid.cosid.Radix36CosIdGenerator.DEFAULT_MACHINE_BIT;
import static me.ahoo.cosid.cosid.Radix36CosIdGenerator.DEFAULT_SEQUENCE_BIT;
import static me.ahoo.cosid.cosid.Radix36CosIdGenerator.DEFAULT_TIMESTAMP_BIT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.test.ConcurrentGenerateStingSpec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class Radix36CosIdGeneratorTest {
    private final Radix36CosIdGenerator cosIdGenerator = new Radix36CosIdGenerator(1);

    @Test
    void generateAsString() {
        String id1 = cosIdGenerator.generateAsString();
        String id2 = cosIdGenerator.generateAsString();
        assertThat(id1.length(), equalTo(17));
        assertThat(id2, greaterThan(id1));
        assertThat(id2.length(), equalTo(id1.length()));
        assertThat(cosIdGenerator.getLastTimestamp(), greaterThan(0L));
    }

    @Test
    void generateAsState() {
        CosIdState state1 = cosIdGenerator.generateAsState();
        CosIdState state2 = cosIdGenerator.generateAsState();
        assertThat(state2, greaterThan(state1));
    }

    @Test
    void stateAsId() {
        CosIdState state = cosIdGenerator.generateAsState();
        String id = cosIdGenerator.getStateParser().asString(state);
        CosIdState state2 = cosIdGenerator.getStateParser().asState(id);
        assertThat(state2, equalTo(state));
        assertThat(state2.getTimestamp(), equalTo(state.getTimestamp()));
        assertThat(state2.getMachineId(), equalTo(state.getMachineId()));
        assertThat(state2.getSequence(), equalTo(state.getSequence()));
    }

    @Test
    void customizeOverflowMachineId() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new Radix36CosIdGenerator(~(-1 << DEFAULT_MACHINE_BIT) + 1);
        });
    }

    @Test
    void generateSlow() {
        Radix36CosIdGenerator cosIdGenerator = new TestRadix36CosIdGenerator();
        CosIdState state1 = cosIdGenerator.generateAsState();
        CosIdState state2 = cosIdGenerator.generateAsState();
        CosIdState state3 = cosIdGenerator.generateAsState();
        assertThat(state3, greaterThan(state2));
        assertThat(state2, greaterThan(state1));

        assertThat(state1.getSequence(), equalTo(1));
        assertThat(state2.getSequence(), equalTo(2));
        assertThat(state3.getSequence(), equalTo(1));
    }

    @Test
    public void generateWhenConcurrentString() {
        new ConcurrentGenerateStingSpec(new Radix36CosIdGenerator(1)).verify();
    }

    static class TestRadix36CosIdGenerator extends Radix36CosIdGenerator {
        private long currentTimeMillis = 1000;

        TestRadix36CosIdGenerator() {
            super(DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, 1, 2);
        }

        @Override
        protected long currentTimeMillis() {
            return currentTimeMillis++;
        }
    }
}
