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

package me.ahoo.cosid.snowflake;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;

import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.stat.generator.IdGeneratorStat;
import org.junit.jupiter.api.Test;

class StringSnowflakeIdTest {

    @Test
    void generateAsStringShouldConvertDelegateGeneratedId() {
        FakeSnowflakeId delegate = new FakeSnowflakeId(3843);
        StringSnowflakeId snowflakeId = new StringSnowflakeId(delegate, Radix62IdConverter.INSTANCE);

        String actual = snowflakeId.generateAsString();

        assertThat(actual, equalTo(Radix62IdConverter.INSTANCE.asString(3843)));
        assertThat(delegate.generateCalls, equalTo(1));
    }

    @Test
    void shouldExposeDelegateSnowflakeProperties() {
        FakeSnowflakeId delegate = new FakeSnowflakeId(3843);
        StringSnowflakeId snowflakeId = new StringSnowflakeId(delegate, Radix62IdConverter.INSTANCE);

        assertThat(snowflakeId.getEpoch(), equalTo(delegate.getEpoch()));
        assertThat(snowflakeId.getTimestampBit(), equalTo(delegate.getTimestampBit()));
        assertThat(snowflakeId.getMachineBit(), equalTo(delegate.getMachineBit()));
        assertThat(snowflakeId.getSequenceBit(), equalTo(delegate.getSequenceBit()));
        assertThat(snowflakeId.getMaxTimestamp(), equalTo(delegate.getMaxTimestamp()));
        assertThat(snowflakeId.getMaxMachineId(), equalTo(delegate.getMaxMachineId()));
        assertThat(snowflakeId.getMaxSequence(), equalTo(delegate.getMaxSequence()));
        assertThat(snowflakeId.getLastTimestamp(), equalTo(delegate.getLastTimestamp()));
        assertThat(snowflakeId.getLastTimestampAsMilliseconds(), equalTo(delegate.getLastTimestampAsMilliseconds()));
        assertThat(snowflakeId.getMachineId(), equalTo(delegate.getMachineId()));
    }

    @Test
    void statShouldExposeDecoratorActualAndConverter() {
        FakeSnowflakeId delegate = new FakeSnowflakeId(3843);
        StringSnowflakeId snowflakeId = new StringSnowflakeId(delegate, Radix62IdConverter.INSTANCE);

        IdGeneratorStat stat = snowflakeId.stat();

        assertThat(stat.getKind(), equalTo(StringSnowflakeId.class.getSimpleName()));
        assertThat(stat.getActual(), sameInstance(delegate.stat()));
        assertThat(stat.getConverter(), equalTo(Radix62IdConverter.INSTANCE.stat()));
    }

    private static final class FakeSnowflakeId implements SnowflakeId {
        private final long generatedId;
        private final IdGeneratorStat stat = SnowflakeId.super.stat();
        private int generateCalls;

        private FakeSnowflakeId(long generatedId) {
            this.generatedId = generatedId;
        }

        @Override
        public long generate() {
            generateCalls++;
            return generatedId;
        }

        @Override
        public long getEpoch() {
            return 1000;
        }

        @Override
        public int getTimestampBit() {
            return 41;
        }

        @Override
        public int getMachineBit() {
            return 10;
        }

        @Override
        public int getSequenceBit() {
            return 12;
        }

        @Override
        public long getMaxTimestamp() {
            return 4095;
        }

        @Override
        public int getMaxMachineId() {
            return 1023;
        }

        @Override
        public long getMaxSequence() {
            return 4095;
        }

        @Override
        public long getLastTimestamp() {
            return 12345;
        }

        @Override
        public int getMachineId() {
            return 7;
        }

        @Override
        public IdGeneratorStat stat() {
            return stat;
        }
    }
}
