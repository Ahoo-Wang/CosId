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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import me.ahoo.cosid.machine.ClockBackwardsSynchronizer;
import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;
import me.ahoo.cosid.snowflake.exception.ClockTooManyBackwardsException;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;

import org.junit.jupiter.api.Test;

class ClockSyncCosIdGeneratorTest {

    @Test
    void shouldDelegateGeneratorMetadata() {
        FakeCosIdGenerator actual = new FakeCosIdGenerator(7, 123, RadixCosIdStateParser.DEFAULT);
        ClockSyncCosIdGenerator generator = new ClockSyncCosIdGenerator(actual, new RecordingSynchronizer());

        assertSame(actual, generator.getActual());
        assertEquals(7, generator.getMachineId());
        assertEquals(123, generator.getLastTimestamp());
        assertSame(RadixCosIdStateParser.DEFAULT, generator.getStateParser());
    }

    @Test
    void generateAsStateShouldReturnActualStateWhenClockDoesNotMoveBackwards() {
        CosIdState expected = new CosIdState(200, 7, 1);
        FakeCosIdGenerator actual = new FakeCosIdGenerator(7, 199, RadixCosIdStateParser.DEFAULT).thenReturn(expected);
        RecordingSynchronizer synchronizer = new RecordingSynchronizer();
        ClockSyncCosIdGenerator generator = new ClockSyncCosIdGenerator(actual, synchronizer);

        assertSame(expected, generator.generateAsState());
        assertEquals(1, actual.generateCalls);
        assertEquals(0, synchronizer.syncCalls);
    }

    @Test
    void generateAsStateShouldSynchronizeLastTimestampAndRetryOnceWhenClockMovesBackwards() {
        CosIdState expected = new CosIdState(124, 7, 1);
        FakeCosIdGenerator actual = new FakeCosIdGenerator(7, 123, RadixCosIdStateParser.DEFAULT)
            .thenThrowClockBackwards()
            .thenReturn(expected);
        RecordingSynchronizer synchronizer = new RecordingSynchronizer();
        ClockSyncCosIdGenerator generator = new ClockSyncCosIdGenerator(actual, synchronizer);

        assertSame(expected, generator.generateAsState());
        assertEquals(2, actual.generateCalls);
        assertEquals(1, synchronizer.syncCalls);
        assertEquals(123, synchronizer.lastTimestamp);
    }

    @Test
    void statShouldWrapActualGeneratorAndParserStats() {
        FakeCosIdGenerator actual = new FakeCosIdGenerator(7, 123, RadixCosIdStateParser.DEFAULT);
        ClockSyncCosIdGenerator generator = new ClockSyncCosIdGenerator(actual, new RecordingSynchronizer());

        IdGeneratorStat stat = generator.stat();

        assertEquals("ClockSyncCosIdGenerator", stat.getKind());
        assertEquals(actual.stat(), stat.getActual());
        assertEquals("RadixCosIdStateParser", stat.getConverter().getKind());
    }

    private static final class RecordingSynchronizer implements ClockBackwardsSynchronizer {
        private int syncCalls;
        private long lastTimestamp = -1;

        @Override
        public void sync(long lastTimestamp) {
            syncUninterruptibly(lastTimestamp);
        }

        @Override
        public void syncUninterruptibly(long lastTimestamp) throws ClockTooManyBackwardsException {
            syncCalls++;
            this.lastTimestamp = lastTimestamp;
        }
    }

    private static final class FakeCosIdGenerator implements CosIdGenerator {
        private final int machineId;
        private long lastTimestamp;
        private final CosIdIdStateParser parser;
        private boolean throwClockBackwards;
        private CosIdState nextState = new CosIdState(1, 1, 1);
        private int generateCalls;

        private FakeCosIdGenerator(int machineId, long lastTimestamp, CosIdIdStateParser parser) {
            this.machineId = machineId;
            this.lastTimestamp = lastTimestamp;
            this.parser = parser;
        }

        private FakeCosIdGenerator thenThrowClockBackwards() {
            this.throwClockBackwards = true;
            return this;
        }

        private FakeCosIdGenerator thenReturn(CosIdState nextState) {
            this.nextState = nextState;
            return this;
        }

        @Override
        public int getMachineId() {
            return machineId;
        }

        @Override
        public long getLastTimestamp() {
            return lastTimestamp;
        }

        @Override
        public CosIdIdStateParser getStateParser() {
            return parser;
        }

        @Override
        public CosIdState generateAsState() {
            generateCalls++;
            if (throwClockBackwards) {
                throwClockBackwards = false;
                throw new ClockBackwardsException(lastTimestamp, lastTimestamp - 1);
            }
            lastTimestamp = nextState.getTimestamp();
            return nextState;
        }
    }
}
