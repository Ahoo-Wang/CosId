# CosId Sharding Algorithms as Spring Beans for ShardingSphere

Below is a `@Configuration` class that registers CosId sharding algorithms as Spring beans, which can then be referenced in your ShardingSphere configuration via the `ALGORITHM` type.

```java
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

package com.example.config;

import me.ahoo.cosid.sharding.CachedSharding;
import me.ahoo.cosid.sharding.IntervalStep;
import me.ahoo.cosid.sharding.IntervalTimeline;
import me.ahoo.cosid.sharding.ModCycle;
import me.ahoo.cosid.sharding.Sharding;

import com.google.common.collect.Range;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * CosId sharding algorithm Spring beans for ShardingSphere.
 *
 * <p>Register these beans and reference them in ShardingSphere YAML
 * using {@code algorithmName} under the sharding-algorithms section.
 */
@Configuration
public class CosIdShardingConfiguration {

    /**
     * Mod sharding algorithm for order_id.
     *
     * <p>Distributes order IDs across 4 shards using modulo:
     * t_order_0, t_order_1, t_order_2, t_order_3.
     *
     * @return mod-based Sharding bean for Long order IDs
     */
    @Bean
    public Sharding<Long> orderModShardingAlgorithm() {
        return new ModCycle<>(4, "t_order_");
    }

    /**
     * Monthly interval sharding algorithm for create_time.
     *
     * <p>Distributes rows into monthly tables from 2020-01 through 2030-01:
     * t_order_202001, t_order_202002, ..., t_order_202912.
     *
     * <p>Uses {@link CachedSharding} to cache range-sharding results
     * for frequently queried date ranges.
     *
     * @return interval-based Sharding bean for LocalDateTime
     */
    @Bean
    public Sharding<LocalDateTime> createTimeIntervalShardingAlgorithm() {
        DateTimeFormatter suffixFormatter = DateTimeFormatter.ofPattern("yyyyMM");

        IntervalTimeline timeline = new IntervalTimeline(
            "t_order_",
            Range.closedOpen(
                LocalDateTime.of(2020, 1, 1, 0, 0),
                LocalDateTime.of(2030, 1, 1, 0, 0)
            ),
            IntervalStep.of(ChronoUnit.MONTHS),
            suffixFormatter
        );

        return new CachedSharding<>(timeline);
    }
}
```

## ShardingSphere YAML Reference

In your ShardingSphere YAML configuration, reference the Spring beans by name:

```yaml
rules:
  - !SHARDING
    tables:
      t_order:
        actualDataNodes: ds_${0..0}.t_order_${0..3}
        tableStrategy:
          standard:
            shardingColumn: order_id
            shardingAlgorithmName: order-mod
        keyGenerateStrategy:
          column: order_id
          keyGeneratorName: cosid-snowflake

    # Register the second sharding table with interval sharding
    t_order_by_time:
      actualDataNodes: ds_${0..0}.t_order_${['202001', '202002', ..., '202912']}
      tableStrategy:
        standard:
          shardingColumn: create_time
          shardingAlgorithmName: create-time-interval

    shardingAlgorithms:
      order-mod:
        type: ALGORITHM
        props:
          algorithmClassName: com.example.config.CosIdShardingConfiguration
      create-time-interval:
        type: ALGORITHM
        props:
          algorithmClassName: com.example.config.CosIdShardingConfiguration
```

**Note:** When using the Spring bean approach, you configure the algorithms as standard ShardingSphere `StandardShardingAlgorithm` beans. ShardingSphere 5.x auto-discovers beans of type `ShardingAlgorithm`. Since CosId's `Sharding` interface provides both precise (`PreciseSharding`) and range (`RangeSharding`) operations, you may need an adapter if ShardingSphere expects its own `ShardingAlgorithm` interface.

## Key Points

1. **`ModCycle<Long>`** -- Takes a divisor (number of shards) and a logic name prefix. For `order_id % 4`, use divisor=4 with prefix `"t_order_"` to produce node names `t_order_0` through `t_order_3`.

2. **`IntervalTimeline`** -- Takes a logic name prefix, an effective time range (`Range<LocalDateTime>`), an `IntervalStep` (here `MONTHS`), and a `DateTimeFormatter` for the suffix. Tables will be named like `t_order_202001`, `t_order_202002`, etc.

3. **`CachedSharding`** -- Wraps any `Sharding` to cache range-query results. This is recommended for `IntervalTimeline` because range sharding involves computing interval offsets, and repeated queries for the same date range benefit from caching.

4. **Adjust the parameters** to match your actual table naming and shard count.
