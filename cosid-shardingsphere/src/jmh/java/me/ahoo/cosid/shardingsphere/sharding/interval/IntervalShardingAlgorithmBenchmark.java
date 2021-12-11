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
import me.ahoo.cosid.shardingsphere.sharding.CosIdAlgorithm;
import org.apache.shardingsphere.sharding.algorithm.sharding.datetime.IntervalShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class IntervalShardingAlgorithmBenchmark {
    private final static ZoneOffset ZONE_OFFSET = ZoneOffset.of("+8");
    private final static LocalDateTime LOWER_DATETIME = LocalDateTime.of(2021, 12, 8, 22, 0, 0);
    private final static long LOWER_TS = LOWER_DATETIME.toInstant(ZONE_OFFSET).toEpochMilli();
    private final static int TOTAL_MOUTHS = 100;
    private final static int TOTAL_RANGE = TOTAL_MOUTHS * 10;
    private final static LocalDateTime UPPER_DATETIME = LOWER_DATETIME.plusMonths(TOTAL_MOUTHS);

    private final static String LOGIC_TABLE_NAME = "t_ldt";
    private final static String FORMATTER_PATTERN = "_yyyyMM";
    AbstractIntervalShardingAlgorithm dateIntervalShardingAlgorithm;
    AbstractIntervalShardingAlgorithm datetimeIntervalShardingAlgorithm;
    IntervalShardingAlgorithm officeIntervalShardingAlgorithm;
    private PreciseShardingValue<Comparable<?>>[] randomPreciseTsValues;
    private RangeShardingValue<Comparable<?>>[] randomRangeTsValues;

    private PreciseShardingValue<Comparable<?>>[] randomPreciseDtValues;
    private RangeShardingValue<Comparable<?>>[] randomRangeDtValues;

    @Setup
    public void init() {
        Properties properties = new Properties();
        properties.setProperty(CosIdAlgorithm.LOGIC_NAME_KEY, LOGIC_TABLE_NAME);
        properties.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_LOWER_KEY, LOWER_DATETIME.toString());
        properties.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_UPPER_KEY, UPPER_DATETIME.toString());
        properties.setProperty(AbstractIntervalShardingAlgorithm.SHARDING_SUFFIX_FORMAT_KEY, FORMATTER_PATTERN);
        properties.setProperty(AbstractIntervalShardingAlgorithm.INTERVAL_UNIT_KEY, "MONTHS");
        properties.setProperty(AbstractIntervalShardingAlgorithm.INTERVAL_AMOUNT_KEY, "1");
        dateIntervalShardingAlgorithm = new DateIntervalShardingAlgorithm();
        dateIntervalShardingAlgorithm.setProps(properties);
        dateIntervalShardingAlgorithm.init();

        datetimeIntervalShardingAlgorithm = new LocalDateTimeIntervalShardingAlgorithm();
        datetimeIntervalShardingAlgorithm.setProps(properties);
        datetimeIntervalShardingAlgorithm.init();

        officeIntervalShardingAlgorithm = new IntervalShardingAlgorithm();
        properties.setProperty("datetime-pattern", "yyyy-MM-dd HH:mm:ss");
        properties.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_LOWER_KEY, "2021-12-08 22:00:00");
        properties.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_UPPER_KEY, "2035-11-02 22:00:00");
        officeIntervalShardingAlgorithm.setProps(properties);
        officeIntervalShardingAlgorithm.init();

        /**
         * 缓存随机分片值，降低基准测试运行时生成随机测试值产生的噪音
         */
        randomPreciseTsValues = new PreciseShardingValue[TOTAL_MOUTHS];
        randomPreciseDtValues = new PreciseShardingValue[TOTAL_MOUTHS];
        for (int i = 0; i < randomPreciseTsValues.length; i++) {
            randomPreciseTsValues[i] = generateRandomTs();
            randomPreciseDtValues[i] = generateRandomLocalDateTime();
        }

        randomRangeTsValues = new RangeShardingValue[TOTAL_RANGE];
        randomRangeDtValues = new RangeShardingValue[TOTAL_RANGE];
        for (int i = 0; i < randomRangeTsValues.length; i++) {
            randomRangeTsValues[i] = generateRandomRangeTs();
            randomRangeDtValues[i] = generateRandomRangeLocalDateTime();
        }

    }

    private final static String COLUMN_NAME = "create_time";

    private int getRandomMonth() {
        return getRandomMonth(0);
    }

    private int getRandomMonth(int origin) {
        return ThreadLocalRandom.current().nextInt(origin, TOTAL_MOUTHS);
    }

    private PreciseShardingValue<Comparable<?>> generateRandomTs() {
        long randomPlusTs = ChronoUnit.MONTHS.getDuration().toMillis() * getRandomMonth();
        Timestamp randomTs = new java.sql.Timestamp(LOWER_TS + randomPlusTs);
        return new PreciseShardingValue(LOGIC_TABLE_NAME, COLUMN_NAME, randomTs);
    }

    private RangeShardingValue<Comparable<?>> generateRandomRangeTs() {
        int randomMonthLower = getRandomMonth();
        long randomPlusTsLower = ChronoUnit.MONTHS.getDuration().toMillis() * randomMonthLower;
        Timestamp randomTsLower = new java.sql.Timestamp(LOWER_TS + randomPlusTsLower);
        int randomMonthUpper = getRandomMonth(randomMonthLower);
        long randomPlusTsUpper = ChronoUnit.MONTHS.getDuration().toMillis() * randomMonthUpper;
        Timestamp randomTsUpper = new java.sql.Timestamp(LOWER_TS + randomPlusTsUpper);
        return new RangeShardingValue(LOGIC_TABLE_NAME, COLUMN_NAME, Range.closed(randomTsLower, randomTsUpper));
    }

    private PreciseShardingValue<Comparable<?>> generateRandomLocalDateTime() {
        LocalDateTime randomLocalDateTime = LOWER_DATETIME.plusMonths(getRandomMonth());
        return new PreciseShardingValue(LOGIC_TABLE_NAME, COLUMN_NAME, randomLocalDateTime);
    }

    private RangeShardingValue<Comparable<?>> generateRandomRangeLocalDateTime() {
        int randomMonthLower = getRandomMonth();
        LocalDateTime randomLocalDateTimeLower = LOWER_DATETIME.plusMonths(randomMonthLower);
        int randomMonthUpper = getRandomMonth(randomMonthLower);
        LocalDateTime randomLocalDateTimeUpper = LOWER_DATETIME.plusMonths(randomMonthUpper);
        return new RangeShardingValue(LOGIC_TABLE_NAME, COLUMN_NAME, Range.closed(randomLocalDateTimeLower, randomLocalDateTimeUpper));
    }


    public PreciseShardingValue<Comparable<?>> getRandomTs() {
        int randomIdx = ThreadLocalRandom.current().nextInt(0, TOTAL_MOUTHS);
        return randomPreciseTsValues[randomIdx];
    }


    public RangeShardingValue<Comparable<?>> getRandomRangeTs() {
        int randomIdx = ThreadLocalRandom.current().nextInt(0, TOTAL_RANGE);
        return randomRangeTsValues[randomIdx];
    }


    public PreciseShardingValue<Comparable<?>> getRandomLocalDateTime() {
        int randomIdx = ThreadLocalRandom.current().nextInt(0, TOTAL_MOUTHS);
        return randomPreciseDtValues[randomIdx];
    }


    public RangeShardingValue<Comparable<?>> getRandomRangeLocalDateTime() {
        int randomIdx = ThreadLocalRandom.current().nextInt(0, TOTAL_RANGE);
        return randomRangeDtValues[randomIdx];
    }

    @SuppressWarnings("unchecked")
    @Benchmark
    public String cosid_precise_timestamp() {
        return dateIntervalShardingAlgorithm.doSharding(dateIntervalShardingAlgorithm.getIntervalTimeline().getAllNodes(), getRandomTs());
    }

    @SuppressWarnings("unchecked")
    @Benchmark
    public Collection<String> cosid_range_timestamp() {
        return dateIntervalShardingAlgorithm.doSharding(dateIntervalShardingAlgorithm.getIntervalTimeline().getAllNodes(), getRandomRangeTs());
    }

    @SuppressWarnings("unchecked")
    @Benchmark
    public String cosid_precise_local_date_time() {
        return datetimeIntervalShardingAlgorithm.doSharding(dateIntervalShardingAlgorithm.getIntervalTimeline().getAllNodes(), getRandomLocalDateTime());
    }

    @SuppressWarnings("unchecked")
    @Benchmark
    public Collection<String> cosid_range_local_date_time() {
        return datetimeIntervalShardingAlgorithm.doSharding(dateIntervalShardingAlgorithm.getIntervalTimeline().getAllNodes(), getRandomRangeLocalDateTime());
    }

    @Benchmark
    public String office_precise_timestamp() {
        return officeIntervalShardingAlgorithm.doSharding(dateIntervalShardingAlgorithm.getIntervalTimeline().getAllNodes(), getRandomTs());
    }

    @Benchmark
    public Collection<String> office_range_timestamp() {
        return officeIntervalShardingAlgorithm.doSharding(dateIntervalShardingAlgorithm.getIntervalTimeline().getAllNodes(), getRandomRangeTs());
    }
}
