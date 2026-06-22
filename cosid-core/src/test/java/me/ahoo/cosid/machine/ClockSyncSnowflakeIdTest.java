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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;
import me.ahoo.cosid.snowflake.exception.ClockTooManyBackwardsException;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

class ClockSyncSnowflakeIdTest {

    @Test
    void generateShouldDelegateDirectlyWhenClockDoesNotMoveBackwards() {
        RecordingSnowflakeId actual = new RecordingSnowflakeId();
        actual.enqueue(101L);
        RecordingClockBackwardsSynchronizer synchronizer = new RecordingClockBackwardsSynchronizer();
        ClockSyncSnowflakeId clockSync = new ClockSyncSnowflakeId(actual, synchronizer);

        assertEquals(101L, clockSync.generate());

        assertEquals(1, actual.generateCalls);
        assertTrue(synchronizer.syncUninterruptiblyCalls.isEmpty());
    }

    @Test
    void generateShouldSynchronizeWithActualLastTimestampInMillisecondsAndRetryOnce() {
        RecordingSnowflakeId actual = new RecordingSnowflakeId();
        actual.lastTimestampAsMilliseconds = 123_456L;
        actual.enqueue(new ClockBackwardsException(123, 122));
        actual.enqueue(202L);
        RecordingClockBackwardsSynchronizer synchronizer = new RecordingClockBackwardsSynchronizer();
        ClockSyncSnowflakeId clockSync = new ClockSyncSnowflakeId(actual, synchronizer);

        assertEquals(202L, clockSync.generate());

        assertEquals(2, actual.generateCalls);
        assertEquals(List.of(123_456L), synchronizer.syncUninterruptiblyCalls);
    }

    @Test
    void generateShouldPropagateRetryFailureAfterSynchronizing() {
        RecordingSnowflakeId actual = new RecordingSnowflakeId();
        actual.lastTimestampAsMilliseconds = 900L;
        actual.enqueue(new ClockBackwardsException(9, 8));
        actual.enqueue(new IllegalStateException("retry failed"));
        RecordingClockBackwardsSynchronizer synchronizer = new RecordingClockBackwardsSynchronizer();
        ClockSyncSnowflakeId clockSync = new ClockSyncSnowflakeId(actual, synchronizer);

        IllegalStateException exception = assertThrows(IllegalStateException.class, clockSync::generate);

        assertEquals("retry failed", exception.getMessage());
        assertEquals(2, actual.generateCalls);
        assertEquals(List.of(900L), synchronizer.syncUninterruptiblyCalls);
    }

    @Test
    void generateShouldPropagateSynchronizerFailureWithoutRetrying() {
        RecordingSnowflakeId actual = new RecordingSnowflakeId();
        actual.lastTimestampAsMilliseconds = 700L;
        actual.enqueue(new ClockBackwardsException(7, 6));
        RecordingClockBackwardsSynchronizer synchronizer = new RecordingClockBackwardsSynchronizer();
        synchronizer.failure = new ClockTooManyBackwardsException(7, 1, 5);
        ClockSyncSnowflakeId clockSync = new ClockSyncSnowflakeId(actual, synchronizer);

        ClockTooManyBackwardsException exception = assertThrows(ClockTooManyBackwardsException.class, clockSync::generate);

        assertSame(synchronizer.failure, exception);
        assertEquals(1, actual.generateCalls);
        assertEquals(List.of(700L), synchronizer.syncUninterruptiblyCalls);
    }

    @Test
    void shouldDelegateSnowflakeMetadataToActualGenerator() {
        RecordingSnowflakeId actual = new RecordingSnowflakeId();
        ClockSyncSnowflakeId clockSync = new ClockSyncSnowflakeId(actual, new RecordingClockBackwardsSynchronizer());

        assertSame(actual, clockSync.getActual());
        assertEquals(actual.getEpoch(), clockSync.getEpoch());
        assertEquals(actual.getTimestampBit(), clockSync.getTimestampBit());
        assertEquals(actual.getMachineBit(), clockSync.getMachineBit());
        assertEquals(actual.getSequenceBit(), clockSync.getSequenceBit());
        assertEquals(actual.isSafeJavascript(), clockSync.isSafeJavascript());
        assertEquals(actual.getMaxTimestamp(), clockSync.getMaxTimestamp());
        assertEquals(actual.getMaxMachineId(), clockSync.getMaxMachineId());
        assertEquals(actual.getMaxSequence(), clockSync.getMaxSequence());
        assertEquals(actual.getLastTimestamp(), clockSync.getLastTimestamp());
        assertEquals(actual.getLastTimestampAsMilliseconds(), clockSync.getLastTimestampAsMilliseconds());
        assertEquals(actual.getMachineId(), clockSync.getMachineId());
    }

    private static final class RecordingSnowflakeId implements SnowflakeId {
        private final ArrayDeque<Object> outcomes = new ArrayDeque<>();
        private int generateCalls;
        private long lastTimestamp = 456L;
        private long lastTimestampAsMilliseconds = 456_000L;

        void enqueue(Object outcome) {
            outcomes.add(outcome);
        }

        @Override
        public long generate() {
            generateCalls++;
            Object outcome = outcomes.removeFirst();
            if (outcome instanceof RuntimeException) {
                throw (RuntimeException) outcome;
            }
            return (long) outcome;
        }

        @Override
        public long getEpoch() {
            return 100L;
        }

        @Override
        public int getTimestampBit() {
            return 41;
        }

        @Override
        public int getMachineBit() {
            return 8;
        }

        @Override
        public int getSequenceBit() {
            return 4;
        }

        @Override
        public long getMaxTimestamp() {
            return 4095L;
        }

        @Override
        public int getMaxMachineId() {
            return 255;
        }

        @Override
        public long getMaxSequence() {
            return 15L;
        }

        @Override
        public long getLastTimestamp() {
            return lastTimestamp;
        }

        @Override
        public long getLastTimestampAsMilliseconds() {
            return lastTimestampAsMilliseconds;
        }

        @Override
        public int getMachineId() {
            return 7;
        }
    }

    private static final class RecordingClockBackwardsSynchronizer implements ClockBackwardsSynchronizer {
        private final List<Long> syncUninterruptiblyCalls = new ArrayList<>();
        private ClockTooManyBackwardsException failure;

        @Override
        public void sync(long lastTimestamp) {
            syncUninterruptiblyCalls.add(lastTimestamp);
        }

        @Override
        public void syncUninterruptibly(long lastTimestamp) throws ClockTooManyBackwardsException {
            syncUninterruptiblyCalls.add(lastTimestamp);
            if (failure != null) {
                throw failure;
            }
        }
    }
}
