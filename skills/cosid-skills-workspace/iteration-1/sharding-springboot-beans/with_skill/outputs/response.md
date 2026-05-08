Here is the `@Configuration` class that registers both sharding algorithms as Spring beans for your ShardingSphere integration:

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

import com.google.common.collect.Range;
import me.ahoo.cosid.sharding.CachedSharding;
import me.ahoo.cosid.sharding.IntervalStep;
import me.ahoo.cosid.sharding.IntervalTimeline;
import me.ahoo.cosid.sharding.ModCycle;
import me.ahoo.cosid.sharding.Sharding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Configuration
public class ShardingConfiguration {

    /**
     * Mod sharding for order_id.
     * Routes orders to 4 physical tables based on order_id modulo.
     * Produces table names: t_order_0, t_order_1, t_order_2, t_order_3.
     */
    @Bean
    public Sharding<Long> orderModSharding() {
        return new CachedSharding<>(new ModCycle<>(4, "t_order_"));
    }

    /**
     * Monthly interval sharding for create_time.
     * Routes records to monthly tables from 2024-01 through 2025-12.
     * Produces table names: t_order_202401, t_order_202402, ..., t_order_202512.
     */
    @Bean
    public IntervalTimeline orderIntervalSharding() {
        return new IntervalTimeline(
            "t_order_",
            Range.closed(
                LocalDateTime.of(2024, 1, 1, 0, 0),
                LocalDateTime.of(2025, 12, 31, 23, 59)
            ),
            IntervalStep.of(ChronoUnit.MONTHS),
            DateTimeFormatter.ofPattern("yyyyMM")
        );
    }
}
```

## Dependency setup (Gradle Kotlin DSL)

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-core")
    implementation("me.ahoo.cosid:cosid-spring-boot-starter")
    implementation("org.apache.shardingsphere:shardingsphere-jdbc")
}
```

## Key points

1. **`ModCycle` constructor order is `(int divisor, String logicNamePrefix)`** -- the divisor (number of shards) comes first, then the table name prefix. The bean uses `CachedSharding` to cache range-query sharding results for better throughput at high query volume.

2. **`IntervalTimeline` constructor order is `(String logicNamePrefix, Range<LocalDateTime>, IntervalStep, DateTimeFormatter)`** -- the prefix comes first here. The `Range.closed(...)` defines the effective interval; sharding values outside this range will throw `IllegalArgumentException`, so design the upper bound with future growth in mind.

3. **`Sharding<Long>`** is the common interface returned by the mod bean. It extends both `PreciseSharding<T>` (single-value routing) and `RangeSharding<T>` (range routing), so ShardingSphere can use it for both `=` and `BETWEEN` queries.

4. **`IntervalTimeline`** implements `Sharding<LocalDateTime>` directly, handling both precise and range time-based routing.

5. **Import order** follows the project Checkstyle convention: `me.ahoo.*` imports first, then third-party (`com.google.*`, `org.springframework.*`), then standard Java (`java.time.*`), with blank lines separating each group.
