/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosid.shardingsphere.sharding.interval;

import com.google.common.collect.Range;
import me.ahoo.cosid.provider.DefaultIdGeneratorProvider;
import me.ahoo.cosid.sharding.IntervalTimeline;
import me.ahoo.cosid.shardingsphere.sharding.CosIdAlgorithm;
import me.ahoo.cosid.sharding.ExactCollection;
import me.ahoo.cosid.snowflake.DefaultSnowflakeFriendlyId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import org.apache.shardingsphere.sharding.algorithm.sharding.datetime.IntervalShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.Properties;

/**
 * @author ahoo wang
 */
class IntervalShardingAlgorithmTest {
    private final static String FORMATTER_PATTERN = "_yyyyMM";
    private Properties properties;
    private final ZoneOffset zoneOffset = ZoneOffset.of("+8");
    private final static DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(FORMATTER_PATTERN);

    @BeforeEach
    void init() {
        properties = new Properties();
        properties.setProperty(CosIdAlgorithm.LOGIC_NAME_KEY, "t_ldt");
        properties.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_LOWER_KEY, "2021-12-08T22:00:00");
        properties.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_UPPER_KEY, "2025-11-02T22:00:00");
        properties.setProperty(AbstractIntervalShardingAlgorithm.SHARDING_SUFFIX_FORMAT_KEY, FORMATTER_PATTERN);
        properties.setProperty(AbstractIntervalShardingAlgorithm.INTERVAL_UNIT_KEY, "MONTHS");
        properties.setProperty(AbstractIntervalShardingAlgorithm.INTERVAL_AMOUNT_KEY, "1");
    }

    @Test
    void test_LocalDateTime() {
        AbstractIntervalShardingAlgorithm shardingAlgorithm = new LocalDateTimeIntervalShardingAlgorithm();
        shardingAlgorithm.setProps(properties);
        shardingAlgorithm.init();
        LocalDateTime shardingLower = LocalDateTime.of(2021, 12, 9, 22, 0);
        PreciseShardingValue preciseshardingValue = new PreciseShardingValue<>("t_ldt", "create_time", shardingLower);
        String node = shardingAlgorithm.doSharding(null, preciseshardingValue);
        Assertions.assertEquals("t_ldt_202112", node);

        LocalDateTime shardingUpper = LocalDateTime.of(2022, 5, 9, 22, 0);
        RangeShardingValue rangeShardingValue = new RangeShardingValue<>("t_ldt", "create_time", Range.closed(shardingLower, shardingUpper));
        Collection<String> nodes = shardingAlgorithm.doSharding(null, rangeShardingValue);
        Assertions.assertEquals(new ExactCollection<>("t_ldt_202112", "t_ldt_202201", "t_ldt_202202", "t_ldt_202203", "t_ldt_202204", "t_ldt_202205"), nodes);
    }

    @Test
    void test_Timestamp() {
        AbstractIntervalShardingAlgorithm shardingAlgorithm = new TimestampIntervalShardingAlgorithm();
        shardingAlgorithm.setProps(properties);
        shardingAlgorithm.init();
        long shardingLower = LocalDateTime.of(2021, 12, 9, 22, 0).toInstant(zoneOffset).toEpochMilli();
        PreciseShardingValue preciseshardingValue = new PreciseShardingValue<>("t_ldt", "create_time", shardingLower);
        String node = shardingAlgorithm.doSharding(null, preciseshardingValue);
        Assertions.assertEquals("t_ldt_202112", node);

        long shardingUpper = LocalDateTime.of(2022, 5, 9, 22, 0).toInstant(zoneOffset).toEpochMilli();
        RangeShardingValue rangeShardingValue = new RangeShardingValue<>("t_ldt", "create_time", Range.closed(shardingLower, shardingUpper));
        Collection<String> nodes = shardingAlgorithm.doSharding(null, rangeShardingValue);
        Assertions.assertEquals(new ExactCollection<>("t_ldt_202112", "t_ldt_202201", "t_ldt_202202", "t_ldt_202203", "t_ldt_202204", "t_ldt_202205"), nodes);
    }

    @Test
    void test_TimestampOfSecond() {
        AbstractIntervalShardingAlgorithm shardingAlgorithm = new TimestampOfSecondIntervalShardingAlgorithm();
        shardingAlgorithm.setProps(properties);
        shardingAlgorithm.init();
        long shardingLower = LocalDateTime.of(2021, 12, 9, 22, 0).toInstant(zoneOffset).toEpochMilli() / 1000;
        PreciseShardingValue preciseshardingValue = new PreciseShardingValue<>("t_ldt", "create_time", shardingLower);
        String node = shardingAlgorithm.doSharding(null, preciseshardingValue);
        Assertions.assertEquals("t_ldt_202112", node);

        long shardingUpper = LocalDateTime.of(2022, 5, 9, 22, 0).toInstant(zoneOffset).toEpochMilli() / 1000;
        RangeShardingValue rangeShardingValue = new RangeShardingValue<>("t_ldt", "create_time", Range.closed(shardingLower, shardingUpper));
        Collection<String> nodes = shardingAlgorithm.doSharding(null, rangeShardingValue);
        Assertions.assertEquals(new ExactCollection<>("t_ldt_202112", "t_ldt_202201", "t_ldt_202202", "t_ldt_202203", "t_ldt_202204", "t_ldt_202205"), nodes);
    }

    @Test
    void test_Date() {
        AbstractIntervalShardingAlgorithm shardingAlgorithm = new DateIntervalShardingAlgorithm();
        shardingAlgorithm.setProps(properties);
        shardingAlgorithm.init();
        Date shardingLower = new Date(LocalDateTime.of(2021, 12, 9, 22, 0).toInstant(zoneOffset).toEpochMilli());
        PreciseShardingValue preciseshardingValue = new PreciseShardingValue<>("t_ldt", "create_time", shardingLower);
        String node = shardingAlgorithm.doSharding(null, preciseshardingValue);
        Assertions.assertEquals("t_ldt_202112", node);

        Date shardingUpper = new Date(LocalDateTime.of(2022, 5, 9, 22, 0).toInstant(zoneOffset).toEpochMilli());
        RangeShardingValue rangeShardingValue = new RangeShardingValue<>("t_ldt", "create_time", Range.closed(shardingLower, shardingUpper));
        Collection<String> nodes = shardingAlgorithm.doSharding(null, rangeShardingValue);
        Assertions.assertEquals(new ExactCollection<>("t_ldt_202112", "t_ldt_202201", "t_ldt_202202", "t_ldt_202203", "t_ldt_202204", "t_ldt_202205"), nodes);
    }


    @Test
    void test_String() {
        AbstractIntervalShardingAlgorithm shardingAlgorithm = new StringIntervalShardingAlgorithm();
        shardingAlgorithm.setProps(properties);
        shardingAlgorithm.init();
        String shardingLower = "2021-12-09 22:00:00";
        PreciseShardingValue preciseshardingValue = new PreciseShardingValue<>("t_ldt", "create_time", shardingLower);
        String node = shardingAlgorithm.doSharding(null, preciseshardingValue);
        Assertions.assertEquals("t_ldt_202112", node);

        String shardingUpper ="2022-05-09 22:00:00";
        RangeShardingValue rangeShardingValue = new RangeShardingValue<>("t_ldt", "create_time", Range.closed(shardingLower, shardingUpper));
        Collection<String> nodes = shardingAlgorithm.doSharding(null, rangeShardingValue);
        Assertions.assertEquals(new ExactCollection<>("t_ldt_202112", "t_ldt_202201", "t_ldt_202202", "t_ldt_202203", "t_ldt_202204", "t_ldt_202205"), nodes);
    }

    @Test
    void test_Office() {
        Properties props = new Properties();
        props.setProperty("datetime-pattern", "yyyy-MM-dd HH:mm:ss");
        props.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_LOWER_KEY, "2021-12-08 00:00:00");
        props.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_UPPER_KEY, "2025-11-02 00:00:00");
        props.setProperty(AbstractIntervalShardingAlgorithm.SHARDING_SUFFIX_FORMAT_KEY, FORMATTER_PATTERN);
        props.setProperty(AbstractIntervalShardingAlgorithm.INTERVAL_UNIT_KEY, "MONTHS");
        props.setProperty(AbstractIntervalShardingAlgorithm.INTERVAL_AMOUNT_KEY, "1");
        IntervalShardingAlgorithm shardingAlgorithm = new IntervalShardingAlgorithm();
        shardingAlgorithm.setProps(props);
        shardingAlgorithm.init();

        LocalDateTime lower = LocalDateTime.of(2021, 12, 8, 0, 0);
        LocalDateTime upper = LocalDateTime.of(2023, 1, 1, 0, 0);
        IntervalTimeline.Step step = IntervalTimeline.Step.of(ChronoUnit.MONTHS, 1);
        String logicName = "t_ldt";
        IntervalTimeline intervalTimeline = new IntervalTimeline(logicName, Range.closed(lower, upper), step, FORMATTER);

        Date shardingLower = new java.sql.Timestamp(LocalDateTime.of(2021, 12, 9, 22, 0).toInstant(zoneOffset).toEpochMilli());
        PreciseShardingValue preciseshardingValue = new PreciseShardingValue<>("t_ldt", "create_time", shardingLower);
        String node = shardingAlgorithm.doSharding(intervalTimeline.getEffectiveNodes(), preciseshardingValue);
        Assertions.assertEquals("t_ldt_202112", node);

        Date shardingUpper = new java.sql.Timestamp(LocalDateTime.of(2022, 5, 9, 22, 0).toInstant(zoneOffset).toEpochMilli());
        RangeShardingValue rangeShardingValue = new RangeShardingValue<>("t_ldt", "create_time", Range.closed(shardingLower, shardingUpper));
        Collection<String> nodes = shardingAlgorithm.doSharding(intervalTimeline.getEffectiveNodes(), rangeShardingValue);
        Assertions.assertEquals(new ExactCollection<>("t_ldt_202112", "t_ldt_202201", "t_ldt_202202", "t_ldt_202203", "t_ldt_202204", "t_ldt_202205"), nodes);
    }

    @Test
    void test_Snowflake() {
        String idName = "test";
        SnowflakeId snowflakeId = new MillisecondSnowflakeId(1);
        DefaultSnowflakeFriendlyId defaultSnowflakeFriendlyId = new DefaultSnowflakeFriendlyId(snowflakeId);
        DefaultIdGeneratorProvider.INSTANCE.set(idName, defaultSnowflakeFriendlyId);
        AbstractIntervalShardingAlgorithm shardingAlgorithm = new SnowflakeIntervalShardingAlgorithm();
        properties.setProperty(CosIdAlgorithm.ID_NAME_KEY, idName);
        shardingAlgorithm.setProps(properties);
        shardingAlgorithm.init();

        long shardingLower = defaultSnowflakeFriendlyId.ofFriendlyId("20211209231730192-1-0").getId();
        PreciseShardingValue preciseshardingValue = new PreciseShardingValue<>("t_ldt", "create_time", shardingLower);
        String node = shardingAlgorithm.doSharding(null, preciseshardingValue);
        Assertions.assertEquals("t_ldt_202112", node);
        long shardingUpper = defaultSnowflakeFriendlyId.ofFriendlyId("20220509231730192-1-0").getId();
        RangeShardingValue rangeShardingValue = new RangeShardingValue<>("t_ldt", "create_time", Range.closed(shardingLower, shardingUpper));
        Collection<String> nodes = shardingAlgorithm.doSharding(null, rangeShardingValue);
        Assertions.assertEquals(new ExactCollection<>("t_ldt_202112", "t_ldt_202201", "t_ldt_202202", "t_ldt_202203", "t_ldt_202204", "t_ldt_202205"), nodes);

        shardingLower = snowflakeId.generate();
        preciseshardingValue = new PreciseShardingValue<>("t_ldt", "create_time", shardingLower);
        node = shardingAlgorithm.doSharding(null, preciseshardingValue);
        Assertions.assertEquals("t_ldt" + FORMATTER.format(LocalDateTime.now()), node);

        shardingLower = defaultSnowflakeFriendlyId.getParser().shiftTimestamp(System.currentTimeMillis());
        preciseshardingValue = new PreciseShardingValue<>("t_ldt", "create_time", shardingLower);
        node = shardingAlgorithm.doSharding(null, preciseshardingValue);
        Assertions.assertEquals("t_ldt" + FORMATTER.format(LocalDateTime.now()), node);
    }
}
