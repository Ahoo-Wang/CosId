# [CosId](https://github.com/Ahoo-Wang/CosId) Universal, flexible, high-performance distributed ID generator

> [中文文档](https://github.com/Ahoo-Wang/CosId/blob/main/README.zh-CN.md)

## Introduction

*[CosId](https://github.com/Ahoo-Wang/CosId)* aims to provide a universal, flexible and high-performance distributed ID generator. Two types of ID generators are currently provided:

- `SnowflakeId` : Stand-alone *TPS performance：4,096,000* [JMH Benchmark](#jmh-benchmark) , It mainly solves two major problems of `SnowflakeId`: machine number allocation problem and clock backwards problem and provide a more friendly and flexible experience.
- `SegmentId`: Get a segment (`Step`) ID every time to reduce the network IO request frequency of the `IdSegment` distributor and improve performance.
    - `IdSegmentDistributor`: 
        - `RedisIdSegmentDistributor`: `IdSegment` distributor based on *Redis*.
        - `JdbcIdSegmentDistributor`: The *Jdbc-based* `IdSegment` distributor supports various relational databases.
    - `SegmentChainId`(**recommend**):`SegmentChainId` (*lock-free*) is an enhancement of `SegmentId`, the design diagram is as follows. `PrefetchWorker` maintains a `safe distance`, so that `SegmentChainId` achieves approximately `AtomicLong` *TPS performance (Step 1000): 127,439,148+/s* [JMH Benchmark](#jmh-benchmark) .
        - `PrefetchWorker` maintains a safe distance (`safeDistance`), and supports dynamic `safeDistance` expansion and contraction based on hunger status.

## SnowflakeId

![Snowflake](./docs/Snowflake-identifier.png)

> *SnowflakeId* is a distributed ID algorithm that uses `Long` (64-bit) bit partition to generate ID.
> The general bit allocation scheme is : `timestamp` (41-bit) + `machineId` (10-bit) + `sequence` (12-bit) = 63-bit。

- 41-bit `timestamp` = (1L<<41)/(1000/3600/365) approximately 69 years of timestamp can be stored, that is, the usable absolute time is `EPOCH` + 69 years. Generally, we need to customize `EPOCH` as the product development time. In addition, we can increase the number of allocated bits by compressing other areas， The number of timestamp bits to extend the available time.
- 10-bit `machineId` = (1L<<10) = 1024 That is, 1024 copies of the same business can be deployed (there is no master-slave copy in the Kubernetes concept, and the definition of Kubernetes is directly used here) instances. Generally, there is no need to use so many, so it will be redefined according to the scale of deployment.
- 12-bit `sequence` = (1L<<12) * 1000 = 4096000 That is, a single machine can generate about 409W ID per second, and a global same-service cluster can generate `4096000*1024=4194304000=4.19 billion (TPS)`.

It can be seen from the design of SnowflakeId:

- :thumbsup: The first 41-bit are a `timestamp`,So *SnowflakeId* is local monotonically increasing, and affected by global clock synchronization *SnowflakeId* is global trend increasing.
- :thumbsup: `SnowflakeId` does not have a strong dependency on any third-party middleware, and its performance is also very high.
- :thumbsup: The bit allocation scheme can be flexibly configured according to the needs of the business system to achieve the optimal use effect.
- :thumbsdown: Strong reliance on the local clock, potential clock moved backwards problems will cause ID duplication.
- :thumbsdown: The `machineId` needs to be set manually. If the `machineId` is manually assigned during actual deployment, it will be very inefficient.

---

*[CosId-SnowflakeId](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-core/src/main/java/me/ahoo/cosid/snowflake)*

It mainly solves two major problems of `SnowflakeId`: machine number allocation problem and clock backwards problem and provide a more friendly and flexible experience.

### MachineIdDistributor

> Currently [CosId](https://github.com/Ahoo-Wang/CosId) provides the following three `MachineId` distributors.

#### ManualMachineIdDistributor

```yaml
cosid:
  snowflake:
    machine:
      distributor:
        type: manual
        manual:
          machine-id: 0
```

> Manually distribute `MachineId`

#### StatefulSetMachineIdDistributor

```yaml
cosid:
  snowflake:
    machine:
      distributor:
        type: stateful_set
```

> Use the stable identification ID provided by the `StatefulSet` of `Kubernetes` as the machine number.

#### RedisMachineIdDistributor

![RedisMachineIdDistributor](./docs/RedisMachineIdDistributor.png)

```yaml
cosid:
  snowflake:
    machine:
      distributor:
        type: redis
```

> Use *Redis* as the distribution store for the machine number.

### ClockBackwardsSynchronizer

```yaml
cosid:
  snowflake:
    clock-backwards:
      spin-threshold: 10
      broken-threshold: 2000
```

The default `DefaultClockBackwardsSynchronizer` clock moved backwards synchronizer uses active wait synchronization strategy, `spinThreshold` (default value 10 milliseconds) is used to set the spin wait threshold, when it is greater than `spinThreshold`, use thread sleep to wait for clock synchronization, if it exceeds` BrokenThreshold` (default value 2 seconds) will directly throw a `ClockTooManyBackwardsException` exception.

### MachineStateStorage

```java
public class MachineState {
  public static final MachineState NOT_FOUND = of(-1, -1);
  private final int machineId;
  private final long lastTimeStamp;

  public MachineState(int machineId, long lastTimeStamp) {
    this.machineId = machineId;
    this.lastTimeStamp = lastTimeStamp;
  }

  public int getMachineId() {
    return machineId;
  }

  public long getLastTimeStamp() {
    return lastTimeStamp;
  }

  public static MachineState of(int machineId, long lastStamp) {
    return new MachineState(machineId, lastStamp);
  }
}
```

```yaml
cosid:
  snowflake:
    machine:
      state-storage:
        local:
          state-location: ./cosid-machine-state/
```

The default `LocalMachineStateStorage` local machine state storage uses a local file to store the machine number and the most recent timestamp, which is used as a `MachineState` cache.

### ClockSyncSnowflakeId

```yaml
cosid:
  snowflake:
    share:
      clock-sync: true
```

The default `SnowflakeId` will directly throw a `ClockBackwardsException` when a clock moved backwards occurs, while using the `ClockSyncSnowflakeId` will use the `ClockBackwardsSynchronizer` to actively wait for clock synchronization to regenerate the ID, providing a more user-friendly experience.

### SafeJavaScriptSnowflakeId

```java
SnowflakeId snowflakeId = SafeJavaScriptSnowflakeId.ofMillisecond(1);
```

The `Number.MAX_SAFE_INTEGER` of `JavaScript` has only 53-bit. If the 63-bit `SnowflakeId` is directly returned to the front end, the value will overflow. Usually we can convert `SnowflakeId` to String type or customize `SnowflakeId` Bit allocation is used to shorten the number of bits of `SnowflakeId` so that `ID` does not overflow when it is provided to the front end.

### SnowflakeFriendlyId (Can parse `SnowflakeId` into a more readable `SnowflakeIdState`)

```yaml
cosid:
  snowflake:
    share:
      friendly: true
```

```java
public class SnowflakeIdState {

    private final long id;

    private final int machineId;

    private final long sequence;

    private final LocalDateTime timestamp;
    /**
     * {@link #timestamp}-{@link #machineId}-{@link #sequence}
     */
    private final String friendlyId;
}
```

```java
public interface SnowflakeFriendlyId extends SnowflakeId {

    SnowflakeIdState friendlyId(long id);

    SnowflakeIdState ofFriendlyId(String friendlyId);

    default SnowflakeIdState friendlyId() {
        long id = generate();
        return friendlyId(id);
    }
}
```

```java
        SnowflakeFriendlyId snowflakeFriendlyId=new DefaultSnowflakeFriendlyId(snowflakeId);
        SnowflakeIdState idState = snowflakeFriendlyId.friendlyId();
        idState.getFriendlyId(); //20210623131730192-1-0
```
## SegmentId

![SegmentId](./docs/SegmentId.png)

### RedisIdSegmentDistributor

```yaml
cosid:
  segment:
    enabled: true
    distributor:
      type: redis
```

### JdbcIdSegmentDistributor

> Initialize the `cosid` table

```mysql
create table if not exists cosid
(
    name            varchar(100) not null comment '{namespace}.{name}',
    last_max_id     bigint       not null default 0,
    last_fetch_time bigint       not null,
    constraint cosid_pk
        primary key (name)
) engine = InnoDB;
```

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/test_db
    username: root
    password: root
cosid:
  segment:
    enabled: true
    distributor:
      type: jdbc
      jdbc:
        enable-auto-init-cosid-table: false
        enable-auto-init-id-segment: true
```

After enabling `enable-auto-init-id-segment:true`, the application will try to create the `idSegment` record when it starts to avoid manual creation. Similar to the execution of the following initialization sql script, there is no need to worry about misoperation, because `name` is the primary key.

```mysql
insert into cosid
    (name, last_max_id, last_fetch_time)
    value
    ('namespace.name', 0, unix_timestamp());
```

### SegmentChainId

![SegmentChainId](./docs/SegmentChainId.png)

```yaml
cosid:
  segment:
    enabled: true
    mode: chain
    chain:
      safe-distance: 5
      prefetch-worker:
        core-pool-size: 2
        prefetch-period: 1s
    distributor:
      type: redis
    share:
      offset: 0
      step: 100
    provider:
      bizC:
        offset: 10000
        step: 100
      bizD:
        offset: 10000
        step: 100
```

## IdGeneratorProvider

```yaml
cosid:
  snowflake:
    provider:
      bizA:
        #      timestamp-bit:
        sequence-bit: 12
      bizB:
        #      timestamp-bit:
        sequence-bit: 12
```

```java
IdGenerator idGenerator = idGeneratorProvider.get("bizA");
```

In actual use, we generally do not use the same `IdGenerator` for all business services, but different businesses use different `IdGenerator`, then `IdGeneratorProvider` exists to solve this problem, and it is the container of `IdGenerator` , You can get the corresponding `IdGenerator` by the business name.

### CosIdPlugin (MyBatis Plugin)

> Kotlin DSL

``` kotlin
    implementation("me.ahoo.cosid:cosid-mybatis:${cosidVersion}")
```

```java

@Target({ElementType.FIELD})
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface CosId {
    String value() default IdGeneratorProvider.SHARE;
    
    boolean friendlyId() default false;
}
```

```java
public class LongIdEntity {

    @CosId(value = "safeJs")
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}

public class FriendlyIdEntity {

    @CosId(friendlyId = true)
    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
```

```java
@Mapper
public interface OrderRepository {
    @Insert("insert into t_table (id) value (#{id});")
    void insert(LongIdEntity order);

    @Insert({
            "<script>",
            "insert into t_friendly_table (id)",
            "VALUES" +
                    "<foreach item='item' collection='list' open='' separator=',' close=''>" +
                    "(#{item.id})" +
                    "</foreach>",
            "</script>"})
    void insertList(List<FriendlyIdEntity> list);
}
```

```java
        LongIdEntity entity = new LongIdEntity();
        entityRepository.insert(entity);
        /**
         * {
         *   "id": 208796080181248
         * }
         */
        return entity;
```

### ShardingSphere Plugin

> Kotlin DSL

``` kotlin
    implementation("me.ahoo.cosid:cosid-shardingsphere:${cosidVersion}")
```

#### CosIdKeyGenerateAlgorithm (Distributed-Id)

```yaml
spring:
  shardingsphere:
    rules:
      sharding:
        key-generators:
          cosid:
            type: COSID
            props:
              id-name: __share__
```

#### Interval-based time range sharding algorithm

![CosIdIntervalShardingAlgorithm](docs/CosIdIntervalShardingAlgorithm.png)

- Ease of use: supports multiple data types (`Long`/`LocalDateTime`/`DATE`),The official implementation is to first convert to a string and then convert to `LocalDateTime`, the conversion success rate is affected by the time formatting characters.
- Performance: Compared to  `org.apache.shardingsphere.sharding.algorithm.sharding.datetime.IntervalShardingAlgorithm`,The performance is *1200~4000* times higher.

| **PreciseShardingValue**                                                                                                                     | **RangeShardingValue**                                                                                                                   |
|----------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| ![Throughput Of IntervalShardingAlgorithm - PreciseShardingValue](wiki/img/Throughput-Of-IntervalShardingAlgorithm-PreciseShardingValue.png) | ![Throughput Of IntervalShardingAlgorithm - RangeShardingValue](wiki/img/Throughput-Of-IntervalShardingAlgorithm-RangeShardingValue.png) |

``` shell
gradle cosid-shardingsphere:jmh
```

```
# JMH version: 1.29
# VM version: JDK 11.0.13, OpenJDK 64-Bit Server VM, 11.0.13+8-LTS
# VM options: -Dfile.encoding=UTF-8 -Djava.io.tmpdir=/work/CosId/cosid-shardingsphere/build/tmp/jmh -Duser.country=CN -Duser.language=zh -Duser.variant
# Blackhole mode: full + dont-inline hint
# Warmup: 1 iterations, 10 s each
# Measurement: 1 iterations, 10 s each
# Timeout: 10 min per iteration
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
Benchmark                                                         (days)   Mode  Cnt         Score   Error  Units
IntervalShardingAlgorithmBenchmark.cosid_precise_local_date_time      10  thrpt       53279788.772          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_local_date_time     100  thrpt       38114729.365          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_local_date_time    1000  thrpt       32714318.129          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_local_date_time   10000  thrpt       22317905.643          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_timestamp            10  thrpt       20028091.211          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_timestamp           100  thrpt       19272744.794          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_timestamp          1000  thrpt       17814417.856          ops/s
IntervalShardingAlgorithmBenchmark.cosid_precise_timestamp         10000  thrpt       12384788.025          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_local_date_time        10  thrpt       18716732.080          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_local_date_time       100  thrpt        8436553.492          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_local_date_time      1000  thrpt        1655952.254          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_local_date_time     10000  thrpt         185348.831          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_timestamp              10  thrpt        9410931.643          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_timestamp             100  thrpt        5792861.181          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_timestamp            1000  thrpt        1585344.761          ops/s
IntervalShardingAlgorithmBenchmark.cosid_range_timestamp           10000  thrpt         196663.812          ops/s
IntervalShardingAlgorithmBenchmark.office_precise_timestamp           10  thrpt          72189.800          ops/s
IntervalShardingAlgorithmBenchmark.office_precise_timestamp          100  thrpt          11245.324          ops/s
IntervalShardingAlgorithmBenchmark.office_precise_timestamp         1000  thrpt           1339.128          ops/s
IntervalShardingAlgorithmBenchmark.office_precise_timestamp        10000  thrpt            113.396          ops/s
IntervalShardingAlgorithmBenchmark.office_range_timestamp             10  thrpt          64679.422          ops/s
IntervalShardingAlgorithmBenchmark.office_range_timestamp            100  thrpt           4267.860          ops/s
IntervalShardingAlgorithmBenchmark.office_range_timestamp           1000  thrpt            227.817          ops/s
IntervalShardingAlgorithmBenchmark.office_range_timestamp          10000  thrpt              7.579          ops/s
```

- SmartIntervalShardingAlgorithm
    - type: COSID_INTERVAL
- DateIntervalShardingAlgorithm
    - type: COSID_INTERVAL_DATE
- LocalDateTimeIntervalShardingAlgorithm
    - type: COSID_INTERVAL_LDT
- StringIntervalShardingAlgorithm
    - type: COSID_INTERVAL_STRING
- TimestampIntervalShardingAlgorithm
    - type: COSID_INTERVAL_TS
- TimestampOfSecondIntervalShardingAlgorithm
    - type: COSID_INTERVAL_TS_SECOND
- SnowflakeIntervalShardingAlgorithm
    - type: COSID_INTERVAL_SNOWFLAKE
- SnowflakeFriendlyIntervalShardingAlgorithm
    - type: COSID_INTERVAL_SNOWFLAKE_FRIENDLY

```yaml
spring:
  shardingsphere:
    rules:
      sharding:
        sharding-algorithms:
          alg-name:
            type: COSID_INTERVAL_{type_suffix}
            props:
              logic-name-prefix: logic-name-prefix
              id-name: cosid-name
              datetime-lower: 2021-12-08 22:00:00
              datetime-upper: 2022-12-01 00:00:00
              sharding-suffix-pattern: yyyyMM
              datetime-interval-unit: MONTHS
              datetime-interval-amount: 1
```

#### CosIdModShardingAlgorithm

![CosIdModShardingAlgorithm](docs/CosIdModShardingAlgorithm.png)

- Performance: Compared to  `org.apache.shardingsphere.sharding.algorithm.sharding.datetime.IntervalShardingAlgorithm`,The performance is *1200~4000* times higher.And it has higher stability and no serious performance degradation.

| **PreciseShardingValue**                                                                                                           | **RangeShardingValue**                                                                                                         |
|------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| ![Throughput Of ModShardingAlgorithm - PreciseShardingValue](wiki/img/Throughput-Of-ModShardingAlgorithm-PreciseShardingValue.png) | ![Throughput Of ModShardingAlgorithm - RangeShardingValue](wiki/img/Throughput-Of-ModShardingAlgorithm-RangeShardingValue.png) |

``` shell
gradle cosid-shardingsphere:jmh
```

```
# JMH version: 1.29
# VM version: JDK 11.0.13, OpenJDK 64-Bit Server VM, 11.0.13+8-LTS
# VM options: -Dfile.encoding=UTF-8 -Djava.io.tmpdir=/work/CosId/cosid-shardingsphere/build/tmp/jmh -Duser.country=CN -Duser.language=zh -Duser.variant
# Blackhole mode: full + dont-inline hint
# Warmup: 1 iterations, 10 s each
# Measurement: 1 iterations, 10 s each
# Timeout: 10 min per iteration
# Threads: 1 thread, will synchronize iterations
# Benchmark mode: Throughput, ops/time
Benchmark                                     (divisor)   Mode  Cnt          Score   Error  Units
ModShardingAlgorithmBenchmark.cosid_precise          10  thrpt       121431137.111          ops/s
ModShardingAlgorithmBenchmark.cosid_precise         100  thrpt       119947284.141          ops/s
ModShardingAlgorithmBenchmark.cosid_precise        1000  thrpt       113095657.321          ops/s
ModShardingAlgorithmBenchmark.cosid_precise       10000  thrpt       108435323.537          ops/s
ModShardingAlgorithmBenchmark.cosid_precise      100000  thrpt        84657505.579          ops/s
ModShardingAlgorithmBenchmark.cosid_range            10  thrpt        37397323.508          ops/s
ModShardingAlgorithmBenchmark.cosid_range           100  thrpt        16905691.783          ops/s
ModShardingAlgorithmBenchmark.cosid_range          1000  thrpt         2969820.981          ops/s
ModShardingAlgorithmBenchmark.cosid_range         10000  thrpt          312881.488          ops/s
ModShardingAlgorithmBenchmark.cosid_range        100000  thrpt           31581.396          ops/s
ModShardingAlgorithmBenchmark.office_precise         10  thrpt         9135460.160          ops/s
ModShardingAlgorithmBenchmark.office_precise        100  thrpt         1356582.418          ops/s
ModShardingAlgorithmBenchmark.office_precise       1000  thrpt          104500.125          ops/s
ModShardingAlgorithmBenchmark.office_precise      10000  thrpt            8619.933          ops/s
ModShardingAlgorithmBenchmark.office_precise     100000  thrpt             629.353          ops/s
ModShardingAlgorithmBenchmark.office_range           10  thrpt         5535645.737          ops/s
ModShardingAlgorithmBenchmark.office_range          100  thrpt           83271.925          ops/s
ModShardingAlgorithmBenchmark.office_range         1000  thrpt             911.534          ops/s
ModShardingAlgorithmBenchmark.office_range        10000  thrpt               9.133          ops/s
ModShardingAlgorithmBenchmark.office_range       100000  thrpt               0.208          ops/s
```

```yaml
spring:
  shardingsphere:
    rules:
      sharding:
        sharding-algorithms:
          alg-name:
            type: COSID_MOD
            props:
              mod: 4
              logic-name-prefix: t_table_
```

## Examples

[CosId-Examples](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-example)

> http://localhost:8008/swagger-ui/index.html#/

## Installation

### Gradle

> Kotlin DSL

``` kotlin
    val cosidVersion = "1.4.14";
    implementation("me.ahoo.cosid:cosid-spring-boot-starter:${cosidVersion}")
```

### Maven

```xml
<?xml version="1.0" encoding="UTF-8"?>

<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <modelVersion>4.0.0</modelVersion>
    <artifactId>demo</artifactId>
    <properties>
        <cosid.version>1.4.14</cosid.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-spring-boot-starter</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>

</project>
```

### application.yaml

```yaml
spring:
  shardingsphere:
    datasource:
      names: ds0,ds1
      ds0:
        type: com.zaxxer.hikari.HikariDataSource
        driver-class-name: com.mysql.cj.jdbc.Driver
        jdbcUrl: jdbc:mysql://localhost:3306/cosid_db_0
        username: root
        password: root
      ds1:
        type: com.zaxxer.hikari.HikariDataSource
        driver-class-name: com.mysql.cj.jdbc.Driver
        jdbcUrl: jdbc:mysql://localhost:3306/cosid_db_1
        username: root
        password: root
    props:
      sql-show: true
    rules:
      sharding:
        binding-tables:
          - t_order,t_order_item
        tables:
          cosid:
            actual-data-nodes: ds0.cosid
          t_table:
            actual-data-nodes: ds0.t_table_$->{0..1}
            table-strategy:
              standard:
                sharding-column: id
                sharding-algorithm-name: table-inline
          t_friendly_table:
            actual-data-nodes: ds0.t_friendly_table
          t_order:
            actual-data-nodes: ds$->{0..1}.t_order
            database-strategy:
              standard:
                sharding-column: order_id
                sharding-algorithm-name: order-db-inline
            key-generate-strategy:
              column: order_id
              key-generator-name: order
          t_order_item:
            actual-data-nodes: ds$->{0..1}.t_order_item
            database-strategy:
              standard:
                sharding-column: order_id
                sharding-algorithm-name: order-db-inline
          t_date_log:
            actual-data-nodes: ds0.t_date_log_202112
            key-generate-strategy:
              column: id
              key-generator-name: snowflake
            table-strategy:
              standard:
                sharding-column: create_time
                sharding-algorithm-name: data-log-interval
          t_date_time_log:
            actual-data-nodes: ds0.t_date_time_log_202112
            key-generate-strategy:
              column: id
              key-generator-name: snowflake
            table-strategy:
              standard:
                sharding-column: create_time
                sharding-algorithm-name: data-time-log-interval
          t_timestamp_log:
            actual-data-nodes: ds0.t_timestamp_log_202112
            key-generate-strategy:
              column: id
              key-generator-name: snowflake
            table-strategy:
              standard:
                sharding-column: create_time
                sharding-algorithm-name: timestamp-log-interval
          t_snowflake_log:
            actual-data-nodes: ds0.t_snowflake_log_202112
            table-strategy:
              standard:
                sharding-column: id
                sharding-algorithm-name: snowflake-log-interval
        sharding-algorithms:
          table-inline:
            type: COSID_MOD
            props:
              mod: 2
              logic-name-prefix: t_table_
          order-db-inline:
            type: COSID_MOD
            props:
              mod: 2
              logic-name-prefix: ds
          data-log-interval:
            type: COSID_INTERVAL_DATE
            props:
              logic-name-prefix: t_date_log_
              datetime-lower: 2021-12-08 22:00:00
              datetime-upper: 2022-12-01 00:00:00
              sharding-suffix-pattern: yyyyMM
              datetime-interval-unit: MONTHS
              datetime-interval-amount: 1
          data-time-log-interval:
            type: COSID_INTERVAL_LDT
            props:
              logic-name-prefix: t_date_time_log_
              datetime-lower: 2021-12-08 22:00:00
              datetime-upper: 2022-12-01 00:00:00
              sharding-suffix-pattern: yyyyMM
              datetime-interval-unit: MONTHS
              datetime-interval-amount: 1
          timestamp-log-interval:
            type: COSID_INTERVAL_TS
            props:
              logic-name-prefix: t_timestamp_log_
              datetime-lower: 2021-12-08 22:00:00
              datetime-upper: 2022-12-01 00:00:00
              sharding-suffix-pattern: yyyyMM
              datetime-interval-unit: MONTHS
              datetime-interval-amount: 1
          snowflake-log-interval:
            type: COSID_INTERVAL_SNOWFLAKE
            props:
              logic-name-prefix: t_snowflake_log_
              id-name: snowflake
              datetime-lower: 2021-12-08 22:00:00
              datetime-upper: 2022-12-01 00:00:00
              sharding-suffix-pattern: yyyyMM
              datetime-interval-unit: MONTHS
              datetime-interval-amount: 1
        key-generators:
          snowflake:
            type: COSID
            props:
              id-name: snowflake
          order:
            type: COSID
            props:
              id-name: order

cosid:
  namespace: ${spring.application.name}
  snowflake:
    enabled: true
    #    epoch: 1577203200000
    clock-backwards:
      spin-threshold: 10
      broken-threshold: 2000
    machine:
      #      stable: true
      #      machine-bit: 10
      #      instance-id: ${HOSTNAME}
      distributor:
        type: redis
      #        manual:
      #          machine-id: 0
      state-storage:
        local:
          state-location: ./cosid-machine-state/
    share:
      clock-sync: true
      friendly: true
    provider:
      order_item:
        #        timestamp-bit:
        sequence-bit: 12
      snowflake:
        sequence-bit: 12
      safeJs:
        machine-bit: 3
        sequence-bit: 9
  segment:
    enabled: true
    mode: chain
    chain:
      safe-distance: 5
      prefetch-worker:
        core-pool-size: 2
        prefetch-period: 1s
    distributor:
      type: redis
    share:
      offset: 0
      step: 100
    provider:
      order:
        offset: 10000
        step: 100
      longId:
        offset: 10000
        step: 100
```

## JMH-Benchmark

- The development notebook : MacBook Pro (M1)
- All benchmark tests are carried out on the development notebook.
- Deploying Redis on the development notebook.

### SnowflakeId

``` shell
gradle cosid-core:jmh
# or
java -jar cosid-core/build/libs/cosid-core-1.4.14-jmh.jar -bm thrpt -wi 1 -rf json -f 1
```

```
Benchmark                                                    Mode  Cnt        Score   Error  Units
SnowflakeIdBenchmark.millisecondSnowflakeId_friendlyId      thrpt       4020311.665          ops/s
SnowflakeIdBenchmark.millisecondSnowflakeId_generate        thrpt       4095403.859          ops/s
SnowflakeIdBenchmark.safeJsMillisecondSnowflakeId_generate  thrpt        511654.048          ops/s
SnowflakeIdBenchmark.safeJsSecondSnowflakeId_generate       thrpt        539818.563          ops/s
SnowflakeIdBenchmark.secondSnowflakeId_generate             thrpt       4206843.941          ops/s
```

### RedisChainIdBenchmark

#### Throughput (ops/s)

![RedisChainIdBenchmark-Throughput](./docs/jmh/RedisChainIdBenchmark-Throughput.png)

``` shell
gradle cosid-redis:jmh
# or
java -jar cosid-redis/build/libs/cosid-redis-1.4.14-jmh.jar -bm thrpt -wi 1 -rf json -f 1 RedisChainIdBenchmark
```

```
Benchmark                                   Mode  Cnt          Score          Error  Units
RedisChainIdBenchmark.atomicLong_baseline  thrpt    5  144541334.198 ±  5578137.471  ops/s
RedisChainIdBenchmark.step_1               thrpt    5    1874168.687 ±   310274.706  ops/s
RedisChainIdBenchmark.step_100             thrpt    5  114226113.524 ± 15789563.078  ops/s
RedisChainIdBenchmark.step_1000            thrpt    5  127439148.104 ±  1833743.699  ops/s
```

#### Percentile-Sample (*P9999=0.208 us/op*)

> In statistics, a [percentile](https://en.wikipedia.org/wiki/Percentile) (or a centile) is a score below which a given percentage of scores in its frequency distribution falls (exclusive definition) or a score at or below which a given percentage falls (inclusive definition). For example, the 50th percentile (the median) is the score below which (exclusive) or at or below which (inclusive) 50% of the scores in the distribution may be found.

![RedisChainIdBenchmark-Sample](./docs/jmh/RedisChainIdBenchmark-Sample.png)

```shell
java -jar cosid-redis/build/libs/cosid-redis-1.4.14-jmh.jar -bm sample -wi 1 -rf json -f 1 -tu us step_1000
```

```
Benchmark                                            Mode      Cnt   Score    Error  Units
RedisChainIdBenchmark.step_1000                    sample  1336271   0.024 ±  0.001  us/op
RedisChainIdBenchmark.step_1000:step_1000·p0.00    sample              ≈ 0           us/op
RedisChainIdBenchmark.step_1000:step_1000·p0.50    sample            0.041           us/op
RedisChainIdBenchmark.step_1000:step_1000·p0.90    sample            0.042           us/op
RedisChainIdBenchmark.step_1000:step_1000·p0.95    sample            0.042           us/op
RedisChainIdBenchmark.step_1000:step_1000·p0.99    sample            0.042           us/op
RedisChainIdBenchmark.step_1000:step_1000·p0.999   sample            0.042           us/op
RedisChainIdBenchmark.step_1000:step_1000·p0.9999  sample            0.208           us/op
RedisChainIdBenchmark.step_1000:step_1000·p1.00    sample           37.440           us/op
```

### MySqlChainIdBenchmark

#### Throughput (ops/s)

![MySqlChainIdBenchmark-Throughput](./docs/jmh/MySqlChainIdBenchmark-Throughput.png)

``` shell
gradle cosid-jdbc:jmh
# or
java -jar cosid-jdbc/build/libs/cosid-jdbc-1.4.14-jmh.jar -bm thrpt -wi 1 -rf json -f 1 MySqlChainIdBenchmark
```

```
Benchmark                                   Mode  Cnt          Score         Error  Units
MySqlChainIdBenchmark.atomicLong_baseline  thrpt    5  145294642.937 ±  224876.284  ops/s
MySqlChainIdBenchmark.step_1               thrpt    5      35058.790 ±   36226.041  ops/s
MySqlChainIdBenchmark.step_100             thrpt    5   74575876.804 ± 5590390.811  ops/s
MySqlChainIdBenchmark.step_1000            thrpt    5  123131804.260 ± 1488004.409  ops/s
```

#### Percentile-Sample (*P9999=0.208 us/op*)

![MySqlChainIdBenchmark-Sample](./docs/jmh/MySqlChainIdBenchmark-Sample.png)

```shell
java -jar cosid-jdbc/build/libs/cosid-jdbc-1.4.14-jmh.jar -bm sample -wi 1 -rf json -f 1 -tu us step_1000
```
```
Benchmark                                            Mode      Cnt    Score   Error  Units
MySqlChainIdBenchmark.step_1000                    sample  1286774    0.024 ± 0.001  us/op
MySqlChainIdBenchmark.step_1000:step_1000·p0.00    sample               ≈ 0          us/op
MySqlChainIdBenchmark.step_1000:step_1000·p0.50    sample             0.041          us/op
MySqlChainIdBenchmark.step_1000:step_1000·p0.90    sample             0.042          us/op
MySqlChainIdBenchmark.step_1000:step_1000·p0.95    sample             0.042          us/op
MySqlChainIdBenchmark.step_1000:step_1000·p0.99    sample             0.042          us/op
MySqlChainIdBenchmark.step_1000:step_1000·p0.999   sample             0.083          us/op
MySqlChainIdBenchmark.step_1000:step_1000·p0.9999  sample             0.208          us/op
MySqlChainIdBenchmark.step_1000:step_1000·p1.00    sample           342.528          us/op
```
