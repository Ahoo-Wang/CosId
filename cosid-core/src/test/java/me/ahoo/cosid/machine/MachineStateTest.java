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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MachineStateTest {

    @Test
    void ofShouldCreateStateWithMachineIdAndLastTimestamp() {
        MachineState machineState = MachineState.of(3, 12345);

        assertThat(machineState.getMachineId(), equalTo(3));
        assertThat(machineState.getLastTimeStamp(), equalTo(12345L));
    }

    @Test
    void ofMachineIdShouldUseCurrentTimestamp() {
        long before = System.currentTimeMillis();

        MachineState machineState = MachineState.of(3);

        long after = System.currentTimeMillis();
        assertThat(machineState.getMachineId(), equalTo(3));
        Assertions.assertTrue(machineState.getLastTimeStamp() >= before);
        Assertions.assertTrue(machineState.getLastTimeStamp() <= after);
    }

    @Test
    void ofStateStringShouldParseMachineIdAndLastTimestamp() {
        MachineState machineState = MachineState.of("3|12345");

        assertThat(machineState.getMachineId(), equalTo(3));
        assertThat(machineState.getLastTimeStamp(), equalTo(12345L));
    }

    @Test
    void ofStateStringShouldRejectMalformedState() {
        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class, () -> MachineState.of("3"));

        assertThat(exception.getMessage(), containsString("format error"));
    }

    @Test
    void toStateStringShouldUseDelimiterContract() {
        MachineState machineState = MachineState.of(3, 12345);

        assertThat(machineState.toStateString(), equalTo("3|12345"));
    }

    @Test
    void notFoundShouldUseSentinelMachineIdAndTimestamp() {
        assertThat(MachineState.NOT_FOUND, equalTo(MachineState.of("-1|-1")));
        assertThat(MachineState.NOT_FOUND.getMachineId(), equalTo(-1));
        assertThat(MachineState.NOT_FOUND.getLastTimeStamp(), equalTo(-1L));
    }

    @Test
    void equalsAndHashCodeShouldUseMachineIdOnly() {
        MachineState first = MachineState.of(3, 100);
        MachineState sameMachineWithNewerTimestamp = MachineState.of(3, 200);
        MachineState otherMachine = MachineState.of(4, 100);

        assertThat(first, equalTo(sameMachineWithNewerTimestamp));
        assertThat(first.hashCode(), equalTo(sameMachineWithNewerTimestamp.hashCode()));
        assertThat(first, not(equalTo(otherMachine)));
    }

    @Test
    void toStringShouldExposeFieldsForDiagnostics() {
        String text = MachineState.of(3, 12345).toString();

        assertThat(text, containsString("machineId=3"));
        assertThat(text, containsString("lastTimeStamp=12345"));
    }
}
