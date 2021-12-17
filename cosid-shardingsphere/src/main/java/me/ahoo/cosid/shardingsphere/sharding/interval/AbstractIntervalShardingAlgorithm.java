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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import me.ahoo.cosid.sharding.IntervalStep;
import me.ahoo.cosid.sharding.IntervalTimeline;
import me.ahoo.cosid.shardingsphere.sharding.CosIdAlgorithm;
import me.ahoo.cosid.shardingsphere.sharding.utils.PropertiesUtil;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Properties;

/**
 * 基于间隔的时间范围分片算法
 * <p>
 * 1. 易用性 支持多种数据类型 (Long:时间戳、LocalDateTime、DATE)，而官方的实现是先转换成字符串再转换成 LocalDateTime，转换成功率受时间格式化字符影响
 * <p>
 * 2. 性能
 * <p>
 * -- 2.1 算法复杂度:[O(1)]，而官方的实现使用的是遍历查找[O(N)] {org.apache.shardingsphere.sharding.algorithm.sharding.datetime.IntervalShardingAlgorithm}
 * <p>
 * -- 2.2 降低解析与转换成本，而官方的实现使用的是先转换成字符串，然后再转换成 LocalDateTime
 * <p>
 * 分配策略=[逻辑名] + [分片算法]，分片算法KEY在全局唯一，这种方式显然是不利于缓存优化的，即{@link #doSharding(Collection, RangeShardingValue)}的第一个参数availableTargetNames应该在绑定时已知且稳定，作为实例变量更利于性能。
 *
 * @author ahoo wang
 * @see DateIntervalShardingAlgorithm
 * @see LocalDateTimeIntervalShardingAlgorithm
 * @see SnowflakeIntervalShardingAlgorithm
 * @see TimestampIntervalShardingAlgorithm
 * @see TimestampOfSecondIntervalShardingAlgorithm
 */
public abstract class AbstractIntervalShardingAlgorithm<T extends Comparable<?>> implements StandardShardingAlgorithm<T> {

    public static final String TYPE_PREFIX = CosIdAlgorithm.TYPE_PREFIX + "INTERVAL_";

    public static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    public static final DateTimeFormatter DEFAULT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_PATTERN);

    public static final String DATE_TIME_LOWER_KEY = "datetime-lower";

    public static final String DATE_TIME_UPPER_KEY = "datetime-upper";

    public static final String SHARDING_SUFFIX_FORMAT_KEY = "sharding-suffix-pattern";

    public static final String INTERVAL_UNIT_KEY = "datetime-interval-unit";

    public static final String INTERVAL_AMOUNT_KEY = "datetime-interval-amount";

    private Properties props = new Properties();

    private volatile IntervalTimeline intervalTimeline;

    @Override
    public Properties getProps() {
        return props;
    }

    @Override
    public void setProps(Properties props) {
        this.props = props;
    }

    /**
     * Initialize algorithm.
     */
    @Override
    public void init() {
        String logicNamePrefix = getRequiredValue(CosIdAlgorithm.LOGIC_NAME_PREFIX_KEY);
        LocalDateTime effectiveLower = LocalDateTime.parse(getRequiredValue(DATE_TIME_LOWER_KEY), DEFAULT_DATE_TIME_FORMATTER);
        LocalDateTime effectiveUpper = LocalDateTime.parse(getRequiredValue(DATE_TIME_UPPER_KEY), DEFAULT_DATE_TIME_FORMATTER);
        DateTimeFormatter suffixFormatter = DateTimeFormatter.ofPattern(getRequiredValue(SHARDING_SUFFIX_FORMAT_KEY));
        ChronoUnit stepUnit = ChronoUnit.valueOf(getRequiredValue(INTERVAL_UNIT_KEY));
        int stepAmount = Integer.parseInt(getProps().getProperty(INTERVAL_AMOUNT_KEY, "1"));
        this.intervalTimeline = new IntervalTimeline(logicNamePrefix, Range.closed(effectiveLower, effectiveUpper), IntervalStep.of(stepUnit, stepAmount), suffixFormatter);
    }

    protected String getRequiredValue(String key) {
        return PropertiesUtil.getRequiredValue(getProps(), key);
    }

    @VisibleForTesting
    public IntervalTimeline getIntervalTimeline() {
        return intervalTimeline;
    }

    /**
     * Sharding.
     *
     * @param availableTargetNames available data sources or table names
     * @param shardingValue        sharding value
     * @return sharding result for data source or table name
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<T> shardingValue) {
        LocalDateTime shardingTime = convertShardingValue(shardingValue.getValue());
        return this.intervalTimeline.sharding(shardingTime);
    }

    protected abstract LocalDateTime convertShardingValue(T shardingValue);

    protected Range<LocalDateTime> convertRangeShardingValue(Range<T> shardingValue) {
        if (Range.all().equals(shardingValue)) {
            return Range.all();
        }
        Object endpointValue = shardingValue.hasLowerBound() ? shardingValue.lowerEndpoint() : shardingValue.upperEndpoint();
        if (endpointValue instanceof LocalDateTime) {
            @SuppressWarnings("unchecked")
            Range<LocalDateTime> targetRange = (Range<LocalDateTime>) shardingValue;
            return targetRange;
        }

        if (shardingValue.hasLowerBound() && shardingValue.hasUpperBound()) {
            LocalDateTime lower = convertShardingValue(shardingValue.lowerEndpoint());
            LocalDateTime upper = convertShardingValue(shardingValue.upperEndpoint());
            return Range.range(lower, shardingValue.lowerBoundType(), upper, shardingValue.upperBoundType());
        }

        if (shardingValue.hasLowerBound()) {
            LocalDateTime lower = convertShardingValue(shardingValue.lowerEndpoint());
            if (BoundType.OPEN.equals(shardingValue.lowerBoundType())) {
                return Range.greaterThan(lower);
            }
            return Range.atLeast(lower);
        }

        LocalDateTime upper = convertShardingValue(shardingValue.upperEndpoint());
        if (BoundType.OPEN.equals(shardingValue.upperBoundType())) {
            return Range.lessThan(upper);
        }
        return Range.atMost(upper);
    }

    /**
     * Sharding.
     *
     * @param availableTargetNames available data sources or table names
     * @param shardingValue        sharding value
     * @return sharding results for data sources or table names
     */
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<T> shardingValue) {
        Range<LocalDateTime> shardingRangeTime = convertRangeShardingValue(shardingValue.getValueRange());
        return this.intervalTimeline.sharding(shardingRangeTime);
    }
}
