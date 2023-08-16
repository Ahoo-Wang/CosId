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

package me.ahoo.cosid.stat;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.cosid.Radix36CosIdGenerator;
import me.ahoo.cosid.jvm.UuidGenerator;
import me.ahoo.cosid.segment.DefaultSegmentId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.StringSegmentId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.StringSnowflakeId;
import me.ahoo.cosid.stat.generator.CosIdGeneratorStat;
import me.ahoo.cosid.stat.generator.SegmentIdStat;

import me.ahoo.cosid.stat.generator.SimpleIdGeneratorStat;
import me.ahoo.cosid.stat.generator.SnowflakeIdStat;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StatisticalTest {

    @Test
    void statUuidGenerator() {
        var stat = UuidGenerator.INSTANCE.stat();
        Assertions.assertNotNull(stat);
    }

    @Test
    void statSnowflakeId() {
        var snowflakeId = new MillisecondSnowflakeId(0);
        var stat = snowflakeId.stat();
        Assertions.assertNotNull(stat);
        assertThat(stat, Matchers.instanceOf(SnowflakeIdStat.class));
        var snowflakeIdStat = (SnowflakeIdStat) stat;
        assertThat(snowflakeIdStat.machineId(), equalTo(0));
    }

    @Test
    void statStringSnowflakeId() {
        var snowflakeId = new StringSnowflakeId(new MillisecondSnowflakeId(0), Radix62IdConverter.INSTANCE);
        var stat = snowflakeId.stat();
        Assertions.assertNotNull(stat);
        assertThat(stat, Matchers.instanceOf(SimpleIdGeneratorStat.class));
    }

    @Test
    void statSegmentId() {
        var segmentId = new DefaultSegmentId(new IdSegmentDistributor.Mock());
        var stat = segmentId.stat();
        Assertions.assertNotNull(stat);
        assertThat(stat, Matchers.instanceOf(SegmentIdStat.class));
    }

    @Test
    void statStringSegmentId() {
        var segmentId = new StringSegmentId(new DefaultSegmentId(new IdSegmentDistributor.Mock()), Radix62IdConverter.INSTANCE);
        var stat = segmentId.stat();
        Assertions.assertNotNull(stat);
        assertThat(stat, Matchers.instanceOf(SimpleIdGeneratorStat.class));
    }

    @Test
    void statCosIdGenerator() {
        var cosIdGenerator = new Radix36CosIdGenerator(0);
        var stat = cosIdGenerator.stat();
        Assertions.assertNotNull(stat);
        assertThat(stat, Matchers.instanceOf(CosIdGeneratorStat.class));
        var cosIdGeneratorStat = (CosIdGeneratorStat) stat;
        assertThat(cosIdGeneratorStat.machineId(), equalTo(0));
    }
}