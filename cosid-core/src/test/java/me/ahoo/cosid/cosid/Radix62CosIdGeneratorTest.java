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

import static me.ahoo.cosid.cosid.Radix62CosIdGenerator.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.test.ConcurrentGenerateStingSpec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

class Radix62CosIdGeneratorTest {
    private final Radix62CosIdGenerator radix62CosIdGenerator = new Radix62CosIdGenerator(1);
    
    @Test
    void generateAsString() {
        String id1 = radix62CosIdGenerator.generateAsString();
        String id2 = radix62CosIdGenerator.generateAsString();
        assertThat(id1.length(), equalTo(15));
        assertThat(id2, greaterThan(id1));
        assertThat(id2.length(), equalTo(id1.length()));
        assertThat(radix62CosIdGenerator.getLastTimestamp(), greaterThan(0L));
    }
    
    @Test
    void generateAsState() {
        CosIdState state1 = radix62CosIdGenerator.generateAsState();
        CosIdState state2 = radix62CosIdGenerator.generateAsState();
        assertThat(state2, greaterThan(state1));
    }
    
    @Test
    void customizeOverflowMachineId() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new Radix62CosIdGenerator(~(-1 << DEFAULT_MACHINE_BIT) + 1);
        });
    }
    
    @Test
    void generateSlow() {
        Radix62CosIdGenerator cosIdGenerator = new Radix62CosIdGenerator(DEFAULT_TIMESTAMP_BIT, DEFAULT_MACHINE_BIT, DEFAULT_SEQUENCE_BIT, 1, 2);
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
        new ConcurrentGenerateStingSpec(new Radix62CosIdGenerator(1)).verify();
    }
}
