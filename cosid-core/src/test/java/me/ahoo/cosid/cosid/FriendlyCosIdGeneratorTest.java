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

import me.ahoo.cosid.test.ConcurrentGenerateStingSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.locks.LockSupport;

import static me.ahoo.cosid.cosid.RadixCosIdGenerator.DEFAULT_MACHINE_BIT;
import static me.ahoo.cosid.cosid.RadixCosIdGenerator.DEFAULT_SEQUENCE_BIT;
import static me.ahoo.cosid.cosid.RadixCosIdGenerator.DEFAULT_TIMESTAMP_BIT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

class FriendlyCosIdGeneratorTest {
    private final FriendlyCosIdGenerator cosIdGenerator = new FriendlyCosIdGenerator(1, ZoneId.systemDefault(), true);

    @Test
    void generateAsString() {
        String id1 = cosIdGenerator.generateAsString();
        String id2 = cosIdGenerator.generateAsString();
        assertThat(id1.length(), equalTo(31));
        assertThat(id2, greaterThan(id1));
        assertThat(id2.length(), equalTo(id1.length()));
        assertThat(cosIdGenerator.getLastTimestamp(), greaterThan(0L));
    }

    @Test
    void generateAsStringWhenPadStartFalse() {
        FriendlyCosIdGenerator cosIdGenerator = new FriendlyCosIdGenerator(1, ZoneId.systemDefault(), false);
        String id1 = cosIdGenerator.generateAsString();
        String id2 = cosIdGenerator.generateAsString();
        assertThat(id1.length(), equalTo(21));
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
        assertThat(state2, equalTo(state2));
    }

    @Test
    void customizeOverflowMachineId() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new Radix36CosIdGenerator(~(-1 << DEFAULT_MACHINE_BIT) + 1);
        });
    }

    @Test
    void generateSlow() {
        FriendlyCosIdGenerator cosIdGenerator = new FriendlyCosIdGenerator(DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, 1, 2, ZoneId.systemDefault(), true);
        CosIdState state1 = cosIdGenerator.generateAsState();
        LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
        CosIdState state2 = cosIdGenerator.generateAsState();
        LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
        CosIdState state3 = cosIdGenerator.generateAsState();
        assertThat(state3, greaterThan(state2));
        assertThat(state2, greaterThan(state1));

        assertThat(state1.getSequence(), equalTo(1));
        assertThat(state2.getSequence(), equalTo(2));
        assertThat(state1.getSequence(), equalTo(1));
    }

    @Test
    public void generateWhenConcurrentString() {
        new ConcurrentGenerateStingSpec(new FriendlyCosIdGenerator(1, ZoneId.systemDefault(), true)).verify();
    }
}