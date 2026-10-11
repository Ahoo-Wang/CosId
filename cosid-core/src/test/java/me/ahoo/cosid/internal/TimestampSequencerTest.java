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

package me.ahoo.cosid.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import me.ahoo.cosid.cosid.CosIdState;
import me.ahoo.cosid.cosid.Radix62CosIdGenerator;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.exception.ClockBackwardsException;
import me.ahoo.cosid.snowflake.exception.TimestampOverflowException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

class TimestampSequencerTest {
    private static final long EPOCH = 1_577_203_200_000L;
    
    /**
     * A clock that only moves when told to, and moves by one tick when polled while waiting for the next tick.
     */
    private static final class ControlledClock {
        private final AtomicLong time = new AtomicLong();
        private int pollsBeforeTick = Integer.MAX_VALUE;
        
        ControlledClock(long time) {
            this.time.set(time);
        }
        
        long now() {
            if (--pollsBeforeTick == 0) {
                pollsBeforeTick = Integer.MAX_VALUE;
                return time.incrementAndGet();
            }
            return time.get();
        }
        
        void set(long newTime) {
            time.set(newTime);
        }
        
        /**
         * The clock advances by one after {@code polls} more reads.
         */
        void tickAfter(int polls) {
            pollsBeforeTick = polls;
        }
    }
    
    @Test
    void sequenceIncrementsWithinOneTimeUnit() {
        ControlledClock clock = new ControlledClock(100);
        TimestampSequencer sequencer = new TimestampSequencer(clock::now, 7, 4);
        
        for (long expected = 1; expected <= 3; expected++) {
            assertThat(sequencer.next(), equalTo(100L));
            assertThat(sequencer.getSequence(), equalTo(expected));
        }
    }
    
    @Test
    void sequenceOverflowWaitsForTheNextTimeUnit() {
        ControlledClock clock = new ControlledClock(100);
        TimestampSequencer sequencer = new TimestampSequencer(clock::now, 3, 100);
        for (int i = 0; i < 3; i++) {
            sequencer.next();
        }
        // The fourth id in the same millisecond wraps the 2-bit sequence: it must wait until the clock moves on.
        clock.tickAfter(5);
        
        assertThat(sequencer.next(), equalTo(101L));
        assertThat(sequencer.getSequence(), equalTo(0L));
        assertThat(sequencer.getLastTimestamp(), equalTo(101L));
    }
    
    @Test
    void sequenceRestartsAfterThresholdOnNewTimeUnit() {
        ControlledClock clock = new ControlledClock(100);
        TimestampSequencer sequencer = new TimestampSequencer(clock::now, 15, 2);
        sequencer.next();
        sequencer.next();
        sequencer.next();
        assertThat(sequencer.getSequence(), equalTo(3L));
        
        clock.set(101);
        
        sequencer.next();
        assertThat(sequencer.getSequence(), equalTo(1L));
    }
    
    @Test
    void sequenceContinuesBelowThresholdOnNewTimeUnit() {
        ControlledClock clock = new ControlledClock(100);
        TimestampSequencer sequencer = new TimestampSequencer(clock::now, 15, 8);
        sequencer.next();
        clock.set(101);
        
        sequencer.next();
        
        assertThat(sequencer.getSequence(), equalTo(2L));
    }
    
    @Test
    void clockBackwardsThrows() {
        ControlledClock clock = new ControlledClock(100);
        TimestampSequencer sequencer = new TimestampSequencer(clock::now, 15, 8);
        sequencer.next();
        clock.set(99);
        
        ClockBackwardsException exception = Assertions.assertThrows(ClockBackwardsException.class, sequencer::next);
        
        Assertions.assertNotNull(exception.getMessage());
        assertThat(sequencer.getLastTimestamp(), equalTo(100L));
    }
    
    @Test
    void snowflakeUsesTheSequencerWithItsOwnClock() {
        ControlledClock clock = new ControlledClock(EPOCH + 1000);
        MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(EPOCH, 41, 10, 2, 1, 4) {
            @Override
            protected long getCurrentTime() {
                return clock.now();
            }
        };
        long first = snowflakeId.generate();
        snowflakeId.generate();
        snowflakeId.generate();
        clock.tickAfter(3);
        long rolledOver = snowflakeId.generate();
        
        assertThat(first & snowflakeId.getMaxSequence(), equalTo(1L));
        assertThat(rolledOver & snowflakeId.getMaxSequence(), equalTo(0L));
        assertThat(snowflakeId.getLastTimestamp(), equalTo(EPOCH + 1001));
        
        clock.set(EPOCH + 1000);
        Assertions.assertThrows(ClockBackwardsException.class, snowflakeId::generate);
    }
    
    @Test
    void snowflakeTimestampOverflowThrows() {
        ControlledClock clock = new ControlledClock(EPOCH + 8);
        MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(EPOCH, 3, 10, 12, 1, 4) {
            @Override
            protected long getCurrentTime() {
                return clock.now();
            }
        };
        
        Assertions.assertThrows(TimestampOverflowException.class, snowflakeId::generate);
    }
    
    @Test
    void cosIdGeneratorUsesTheSequencer() {
        ControlledClock clock = new ControlledClock(1000);
        Radix62CosIdGenerator generator = new Radix62CosIdGenerator(44, 20, 2, 1, 4) {
            @Override
            protected long currentTimeMillis() {
                return clock.now();
            }
        };
        CosIdState first = generator.generateAsState();
        generator.generateAsState();
        generator.generateAsState();
        clock.tickAfter(3);
        CosIdState rolledOver = generator.generateAsState();
        
        assertThat(first.getSequence(), equalTo(1));
        assertThat(rolledOver.getTimestamp(), equalTo(1001L));
        assertThat(rolledOver.getSequence(), equalTo(0));
        
        clock.set(999);
        Assertions.assertThrows(ClockBackwardsException.class, generator::generateAsState);
    }
    
    @Test
    void cosIdGeneratorTimestampOverflowThrows() {
        ControlledClock clock = new ControlledClock(16);
        Radix62CosIdGenerator generator = new Radix62CosIdGenerator(4, 20, 16, 1, 4) {
            @Override
            protected long currentTimeMillis() {
                return clock.now();
            }
        };
        
        Assertions.assertThrows(TimestampOverflowException.class, generator::generateAsState);
    }
}
