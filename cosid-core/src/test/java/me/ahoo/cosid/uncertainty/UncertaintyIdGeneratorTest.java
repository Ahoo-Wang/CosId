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

package me.ahoo.cosid.uncertainty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.ahoo.cosid.IdGenerator;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.test.ConcurrentGenerateSpec;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

class UncertaintyIdGeneratorTest {
    private static final int UNCERTAINTY_BITS = 5;

    @Test
    void constructorShouldDeriveBitLayoutFromUncertaintyBits() {
        SequenceIdGenerator actual = new SequenceIdGenerator(1);
        UncertaintyIdGenerator generator = new UncertaintyIdGenerator(actual, UNCERTAINTY_BITS);

        assertSame(actual, generator.getActual());
        assertEquals(UNCERTAINTY_BITS, generator.uncertaintyBits());
        assertEquals(SnowflakeId.TOTAL_BIT - UNCERTAINTY_BITS, generator.originalIdBits());
        assertEquals(1L << UNCERTAINTY_BITS, generator.uncertaintyBound());
        assertEquals((1L << (SnowflakeId.TOTAL_BIT - UNCERTAINTY_BITS)) - 1, generator.maxOriginalId());
    }

    @Test
    void constructorShouldRejectUncertaintyBitsOutsideAvailablePositiveLongBits() {
        SequenceIdGenerator actual = new SequenceIdGenerator(1);

        assertThrows(IllegalArgumentException.class, () -> new UncertaintyIdGenerator(actual, 0));
        assertThrows(IllegalArgumentException.class, () -> new UncertaintyIdGenerator(actual, SnowflakeId.TOTAL_BIT));
    }

    @Test
    void generateShouldPreserveOriginalIdInHighBitsAndPutUncertaintyInLowBits() {
        UncertaintyIdGenerator generator = new UncertaintyIdGenerator(new SequenceIdGenerator(7), UNCERTAINTY_BITS);

        long first = generator.generate();
        long second = generator.generate();

        assertEquals(7, first >>> UNCERTAINTY_BITS);
        assertEquals(8, second >>> UNCERTAINTY_BITS);
        assertTrue((first & (generator.uncertaintyBound() - 1)) < generator.uncertaintyBound());
        assertTrue((second & (generator.uncertaintyBound() - 1)) < generator.uncertaintyBound());
        assertTrue(second > first);
    }

    @Test
    void generateShouldRejectOriginalIdThatCannotFitIntoRemainingBits() {
        long maxOriginalId = (1L << (SnowflakeId.TOTAL_BIT - UNCERTAINTY_BITS)) - 1;
        UncertaintyIdGenerator generator = new UncertaintyIdGenerator(new SequenceIdGenerator(maxOriginalId), UNCERTAINTY_BITS);

        generator.generate();
        OriginalIdOverflowException error = assertThrows(OriginalIdOverflowException.class, generator::generate);

        assertEquals(maxOriginalId + 1, error.originalId());
        assertEquals(SnowflakeId.TOTAL_BIT - UNCERTAINTY_BITS, error.originalIdBits());
        assertEquals(maxOriginalId, error.maxOriginalId());
    }

    @Test
    void generateShouldRemainGloballyIncreasingUnderSmallConcurrentLoadWhenOriginalIdsIncrease() {
        UncertaintyIdGenerator generator = new UncertaintyIdGenerator(new SequenceIdGenerator(1), UNCERTAINTY_BITS);

        new ConcurrentGenerateSpec(4, 256, Duration.ofSeconds(5), generator) {
            @Override
            protected void assertGlobalFirst(long id) {
                assertTrue(id > 0);
            }

            @Override
            protected void assertGlobalEach(long previousId, long id) {
                assertTrue(id > previousId, "id must stay globally increasing because original id occupies high bits");
            }

            @Override
            protected void assertGlobalLast(long lastId) {
                assertEquals(256, lastId >>> UNCERTAINTY_BITS);
            }
        }.verify();
    }

    private static final class SequenceIdGenerator implements IdGenerator {
        private final AtomicLong next;

        private SequenceIdGenerator(long firstId) {
            this.next = new AtomicLong(firstId);
        }

        @Override
        public long generate() {
            return next.getAndIncrement();
        }
    }
}
