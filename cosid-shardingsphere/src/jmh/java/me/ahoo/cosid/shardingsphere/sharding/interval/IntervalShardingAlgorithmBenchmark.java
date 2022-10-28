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

package me.ahoo.cosid.shardingsphere.sharding.interval;

import me.ahoo.cosid.shardingsphere.sharding.CosIdAlgorithm;

import com.google.common.collect.Range;
import org.apache.shardingsphere.infra.datanode.DataNodeInfo;
import org.apache.shardingsphere.sharding.algorithm.sharding.datetime.IntervalShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
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
    private static final ZoneOffset ZONE_OFFSET = ZoneOffset.of("+8");
    private static final LocalDateTime LOWER_DATETIME = LocalDateTime.of(2021, 12, 8, 22, 0, 0);
    private static final long LOWER_TS = LOWER_DATETIME.toInstant(ZONE_OFFSET).toEpochMilli();
    @Param({"10", "100", "1000", "10000"})
    private int days;
    private int totalRange;
    
    private static final String LOGIC_TABLE_NAME = "t_ldt";
    private static final String LOGIC_NAME_PREFIX = LOGIC_TABLE_NAME + "_";
    private static final String FORMATTER_PATTERN = "yyyyMMdd";
    public static final DataNodeInfo DATA_NODE_INFO = new DataNodeInfo(LOGIC_NAME_PREFIX, 6, '0');
    CosIdIntervalShardingAlgorithm cosIdIntervalShardingAlgorithm;
    IntervalShardingAlgorithm officeIntervalShardingAlgorithm;
    private PreciseShardingValue<Comparable<?>>[] randomPreciseTsValues;
    private RangeShardingValue<Comparable<?>>[] randomRangeTsValues;
    private PreciseShardingValue<Comparable<?>>[] randomPreciseDtValues;
    private RangeShardingValue<Comparable<?>>[] randomRangeDtValues;
    
    @Setup
    public void init() {
        totalRange = days * 10;
        LocalDateTime upperDatetime = LOWER_DATETIME.plusDays(days);
        Properties properties = new Properties();
        properties.setProperty(CosIdAlgorithm.LOGIC_NAME_PREFIX_KEY, LOGIC_NAME_PREFIX);
        properties.setProperty(CosIdIntervalShardingAlgorithm.ZONE_ID_KEY, "Asia/Shanghai");
        properties.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_LOWER_KEY, LOWER_DATETIME.format(AbstractIntervalShardingAlgorithm.DEFAULT_DATE_TIME_FORMATTER));
        properties.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_UPPER_KEY, upperDatetime.format(AbstractIntervalShardingAlgorithm.DEFAULT_DATE_TIME_FORMATTER));
        properties.setProperty(AbstractIntervalShardingAlgorithm.SHARDING_SUFFIX_FORMAT_KEY, FORMATTER_PATTERN);
        properties.setProperty(AbstractIntervalShardingAlgorithm.INTERVAL_UNIT_KEY, "DAYS");
        properties.setProperty(AbstractIntervalShardingAlgorithm.INTERVAL_AMOUNT_KEY, "1");
        cosIdIntervalShardingAlgorithm = new CosIdIntervalShardingAlgorithm();
        cosIdIntervalShardingAlgorithm.init(properties);
        
        officeIntervalShardingAlgorithm = new IntervalShardingAlgorithm();
        properties.setProperty("datetime-pattern", AbstractIntervalShardingAlgorithm.DEFAULT_DATE_TIME_PATTERN);
        properties.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_LOWER_KEY, LOWER_DATETIME.format(AbstractIntervalShardingAlgorithm.DEFAULT_DATE_TIME_FORMATTER));
        properties.setProperty(AbstractIntervalShardingAlgorithm.DATE_TIME_UPPER_KEY, upperDatetime.format(AbstractIntervalShardingAlgorithm.DEFAULT_DATE_TIME_FORMATTER));
        officeIntervalShardingAlgorithm.init(properties);
        
        /**
         * 缓存随机分片值，降低基准测试运行时生成随机测试值产生的噪音
         */
        randomPreciseTsValues = new PreciseShardingValue[totalRange];
        randomPreciseDtValues = new PreciseShardingValue[totalRange];
        for (int i = 0; i < randomPreciseTsValues.length; i++) {
            randomPreciseTsValues[i] = generateRandomTs();
            randomPreciseDtValues[i] = generateRandomLocalDateTime();
        }
        
        randomRangeTsValues = new RangeShardingValue[totalRange];
        randomRangeDtValues = new RangeShardingValue[totalRange];
        for (int i = 0; i < randomRangeTsValues.length; i++) {
            randomRangeTsValues[i] = generateRandomRangeTs();
            randomRangeDtValues[i] = generateRandomRangeLocalDateTime();
        }
        
    }
    
    private static final String COLUMN_NAME = "create_time";
    
    private int getRandomInDays() {
        return getRandomInDays(0);
    }
    
    private int getRandomInDays(int origin) {
        return ThreadLocalRandom.current().nextInt(origin, days);
    }
    
    private PreciseShardingValue<Comparable<?>> generateRandomTs() {
        long randomPlusTs = ChronoUnit.DAYS.getDuration().toMillis() * getRandomInDays();
        Timestamp randomTs = new java.sql.Timestamp(LOWER_TS + randomPlusTs);
        return new PreciseShardingValue(LOGIC_TABLE_NAME, COLUMN_NAME, DATA_NODE_INFO, randomTs);
    }
    
    private RangeShardingValue<Comparable<?>> generateRandomRangeTs() {
        int randomLower = getRandomInDays();
        long randomPlusTsLower = ChronoUnit.DAYS.getDuration().toMillis() * randomLower;
        Timestamp randomTsLower = new java.sql.Timestamp(LOWER_TS + randomPlusTsLower);
        int randomUpper = getRandomInDays(randomLower);
        long randomPlusTsUpper = ChronoUnit.DAYS.getDuration().toMillis() * randomUpper;
        Timestamp randomTsUpper = new java.sql.Timestamp(LOWER_TS + randomPlusTsUpper);
        return new RangeShardingValue(LOGIC_TABLE_NAME, COLUMN_NAME, DATA_NODE_INFO, Range.closed(randomTsLower, randomTsUpper));
    }
    
    private PreciseShardingValue<Comparable<?>> generateRandomLocalDateTime() {
        LocalDateTime randomLocalDateTime = LOWER_DATETIME.plusDays(getRandomInDays());
        return new PreciseShardingValue(LOGIC_TABLE_NAME, COLUMN_NAME, DATA_NODE_INFO, randomLocalDateTime);
    }
    
    private RangeShardingValue<Comparable<?>> generateRandomRangeLocalDateTime() {
        int lower = getRandomInDays();
        LocalDateTime randomLocalDateTimeLower = LOWER_DATETIME.plusDays(lower);
        int upper = getRandomInDays(lower);
        LocalDateTime randomLocalDateTimeUpper = LOWER_DATETIME.plusDays(upper);
        return new RangeShardingValue(LOGIC_TABLE_NAME, COLUMN_NAME, DATA_NODE_INFO, Range.closed(randomLocalDateTimeLower, randomLocalDateTimeUpper));
    }
    
    
    public PreciseShardingValue<Comparable<?>> getRandomTs() {
        int randomIdx = ThreadLocalRandom.current().nextInt(0, totalRange);
        return randomPreciseTsValues[randomIdx];
    }
    
    
    public RangeShardingValue<Comparable<?>> getRandomRangeTs() {
        int randomIdx = ThreadLocalRandom.current().nextInt(0, totalRange);
        return randomRangeTsValues[randomIdx];
    }
    
    
    public PreciseShardingValue<Comparable<?>> getRandomLocalDateTime() {
        int randomIdx = ThreadLocalRandom.current().nextInt(0, totalRange);
        return randomPreciseDtValues[randomIdx];
    }
    
    
    public RangeShardingValue<Comparable<?>> getRandomRangeLocalDateTime() {
        int randomIdx = ThreadLocalRandom.current().nextInt(0, totalRange);
        return randomRangeDtValues[randomIdx];
    }
    
    @SuppressWarnings("unchecked")
    @Benchmark
    public String cosid_precise_timestamp() {
        return cosIdIntervalShardingAlgorithm.doSharding(cosIdIntervalShardingAlgorithm.getSharding().getEffectiveNodes(), getRandomTs());
    }
    
    @SuppressWarnings("unchecked")
    @Benchmark
    public Collection<String> cosid_range_timestamp() {
        return cosIdIntervalShardingAlgorithm.doSharding(cosIdIntervalShardingAlgorithm.getSharding().getEffectiveNodes(), getRandomRangeTs());
    }
    
    @SuppressWarnings("unchecked")
    @Benchmark
    public String cosid_precise_local_date_time() {
        return cosIdIntervalShardingAlgorithm.doSharding(cosIdIntervalShardingAlgorithm.getSharding().getEffectiveNodes(), getRandomLocalDateTime());
    }
    
    @SuppressWarnings("unchecked")
    @Benchmark
    public Collection<String> cosid_range_local_date_time() {
        return cosIdIntervalShardingAlgorithm.doSharding(cosIdIntervalShardingAlgorithm.getSharding().getEffectiveNodes(), getRandomRangeLocalDateTime());
    }
    
    @Benchmark
    public String office_precise_timestamp() {
        return officeIntervalShardingAlgorithm.doSharding(cosIdIntervalShardingAlgorithm.getSharding().getEffectiveNodes(), getRandomTs());
    }
    
    @Benchmark
    public Collection<String> office_range_timestamp() {
        return officeIntervalShardingAlgorithm.doSharding(cosIdIntervalShardingAlgorithm.getSharding().getEffectiveNodes(), getRandomRangeTs());
    }
}
