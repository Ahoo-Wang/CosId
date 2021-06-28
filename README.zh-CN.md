# [CosId](https://github.com/Ahoo-Wang/CosId) 通用、灵活、高性能的分布式 ID 生成器

## 介绍

*[CosId](https://github.com/Ahoo-Wang/CosId)* 旨在提供通用、灵活、高性能的分布式系统 ID 生成器。 目前提供了俩大类 ID 生成器：*SnowflakeId* （单机 TPS
性能：409W/s [JMH 基准测试](#jmh-benchmark)）、*RedisIdGenerator* (单机 TPS 性能(步长 1000)：3545W+/s [JMH 基准测试](#jmh-benchmark))。

## SnowflakeId

![Snowflake](./docs/Snowflake-identifier.png)

> *SnowflakeId* 使用 `Long` （64 bits） 位分区来生成 ID 的一种分布式 ID 算法。
> 通用的位分配方案为：`timestamp` (41 bits) + `machineId` (10 bits) + `sequence` (12 bits) = 63 bits 。

- 41 位 `timestamp` = (1L<<41)/(1000/3600/365) 约可以存储 69 年的时间戳，即可以使用的绝对时间为 `EPOCH` + 69 年，一般我们需要自定义 `EPOCH`
  为产品开发时间，另外还可以通过压缩其他区域的分配位数，来增加时间戳位数来延长可用时间。
- 10 位 `machineId` = (1L<<10) = 1024 即相同业务可以部署 1024 个副本 (在 Kubernetes 概念里没有主从副本之分，这里直接沿用 Kubernetes 的定义)
  实例，一般情况下没有必要使用这么多位，所以会根据部署规模需要重新定义。
- 12 位 `sequence` = (1L<<12) * 1000 = 4096000 即单机每秒可生成约 409W 的 ID，全局同业务集群可产生 4096000*1024=419430W=41.9亿(TPS)。

从 *SnowflakeId* 设计上可以看出:

- :thumbsup: `timestamp` 在高位，所以 *SnowflakeId* 是本机单调递增的，受全局时钟同步影响 *SnowflakeId* 是全局趋势递增的。
- :thumbsup: *SnowflakeId* 不对任何第三方中间件有强依赖关系，并且性能也非常高。
- :thumbsup: 位分配方案可以按照业务系统需要灵活配置，来达到最优使用效果。
- :thumbsdown: 强依赖本机时钟，潜在的时钟回拨问题会导致 ID 重复。
- :thumbsdown: `machineId` 需要手动设置，实际部署时如果采用手动分配 `machineId`，会非常低效。

---

*[CosId-SnowflakeId](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-core/src/main/java/me/ahoo/cosid/snowflake)*
主要解决 *SnowflakeId* 俩大问题：机器号分配问题、时钟回拨问题。 并且提供更加友好、灵活的使用体验。

### MachineIdDistributor (MachineId 分配器)

> 目前 *[CosId](https://github.com/Ahoo-Wang/CosId)* 提供了以下三种 `MachineId` 分配器。

#### ManualMachineIdDistributor

```yaml
cosid:
  snowflake:
    manual:
      enabled: true
      machine-id: 1
```

> 手动分配 `MachineId`

#### StatefulSetMachineIdDistributor

```yaml
cosid:
  snowflake:
    stateful-set:
    enabled: true
```

> 使用 `Kubernetes` 的 `StatefulSet` 提供的稳定的标识 ID 作为机器号。

#### RedisMachineIdDistributor

```yaml
cosid:
  snowflake:
    redis:
      enabled: true
```

> 使用 `Redis` 作为机器号的分发存储。

### ClockBackwardsSynchronizer (时钟回拨同步器)

默认提供的 `DefaultClockBackwardsSynchronizer` 时钟回拨同步器使用主动等待同步策略，`spinThreshold`(默认值 20 毫秒) 用于设置自旋等待阈值， 当大于`spinThreshold`
时使用线程休眠等待时钟同步，如果超过`brokenThreshold`(默认值 2 秒)时会直接抛出`ClockTooManyBackwardsException`异常。

### LocalMachineState (本地机器状态存储)

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

默认提供的 `FileLocalMachineState` 本地机器状态存储，使用本地文件存储机器号、最近一次时间戳，用作 `MachineState` 缓存。

### ClockSyncSnowflakeId (主动时钟同步 `SnowflakeId`)

默认 `SnowflakeId` 当发生时钟回拨时会直接抛出 `ClockBackwardsException` 异常，而使用 `ClockSyncSnowflakeId` 会使用 `ClockBackwardsSynchronizer`
主动等待时钟同步来重新生成 ID，提供更加友好的使用体验。

### SafeJavaScriptSnowflakeId (`JavaScript` 安全的 `SnowflakeId`)

```java
SnowflakeId snowflakeId=SafeJavaScriptSnowflakeId.ofMillisecond(1);
```

`JavaScript` 的 `Number.MAX_SAFE_INTEGER` 只有 53 位，如果直接将 63 位的 `SnowflakeId` 返回给前端，那么会值溢出的情况，通常我们可以将`SnowflakeId`转换为
String 类型或者自定义 `SnowflakeId` 位分配来缩短 `SnowflakeId` 的位数 使 `ID` 提供给前端时不溢出。

### SnowflakeIdStateParser (可以将 `SnowflakeId` 解析成可读性更好的 `SnowflakeIdState` )

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
        SnowflakeIdState idState=snowflakeIdStateParser.parse(id);
        idState.getFriendlyId(); //20210623131730192-1-0
```

## RedisIdGenerator

`RedisIdGenerator` 步长设置为 1 时（每次生成`ID`都需要执行一次 *Redis* 网络 IO 请求）*TPS* 性能约为 21W/s ([JMH 基准测试](#jmh-benchmark))，如果在部分场景下我们对 ID 生成的 *TPS* 性能有更高的要求，那么可以选择使用增加每次`ID`分发步长来降低网络 IO 请求频次，提高 `IdGenerator`
性能（比如增加步长为 1000，性能可提升到 3545W+/s [JMH 基准测试](#jmh-benchmark)）。

## IdGeneratorProvider

```yaml
cosid:
  snowflake:
    provider:
      bizA:
        #      epoch:
        #      timestamp-bit:
        #      machine-bit:
        sequence-bit: 12
      bizB:
        #      epoch:
        #      timestamp-bit:
        #      machine-bit:
        sequence-bit: 12
```

```java
IdGenerator idGenerator = idGeneratorProvider.get("bizA");
```

在实际使用中我们一般不会所有业务服务使用同一个 `IdGenerator` ，而是不同的业务使用不同的 `IdGenerator`，那么 `IdGeneratorProvider`
就是为了解决这个问题而存在的，他是 `IdGenerator` 的容器，可以通过业务名来获取相应的 `IdGenerator`。

## Examples

[CosId-Examples](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-example)

## 安装

### Gradle

> Kotlin DSL

``` kotlin
    val cosidVersion = "0.8.6";
    implementation("me.ahoo.cosid:spring-boot-starter-cosid:${cosidVersion}")
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
        <cosid.version>0.8.6</cosid.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>spring-boot-starter-cosid</artifactId>
            <version>${cosid.version}</version>
        </dependency>
    </dependencies>

</project>
```

### application.yaml

```yaml
cosid:
  namespace: ${spring.application.name}
  snowflake:
    #  stateful-set:
    #    enabled: true
    #  manual:
    #    enabled: true
    #    machine-id: 1
    redis:
      enabled: true
    provider:
      order:
        #      epoch:
        #      timestamp-bit:
        #      machine-bit:
        sequence-bit: 12
      user:
        #      epoch:
        #      timestamp-bit:
        #      machine-bit:
        sequence-bit: 12
    enabled: false
  redis:
    enabled: true
    provider:
      order:
        step: 100

```

## JMH-Benchmark

### SnowflakeId

```
Benchmark                                                    Mode  Cnt        Score   Error  Units
SnowflakeIdBenchmark.millisecondSnowflakeId_generate        thrpt       4093924.313          ops/s
SnowflakeIdBenchmark.safeJsMillisecondSnowflakeId_generate  thrpt        511542.292          ops/s
SnowflakeIdBenchmark.safeJsSecondSnowflakeId_generate       thrpt        511939.629          ops/s
SnowflakeIdBenchmark.secondSnowflakeId_generate             thrpt       4204761.870          ops/s
```

### RedisIdGenerator

``` shell
gradle cosid-redis:jmh
```

```
Benchmark                             Mode  Cnt         Score   Error  Units
RedisIdGeneratorBenchmark.step_1     thrpt         216277.251          ops/s
RedisIdGeneratorBenchmark.step_100   thrpt        4006944.185          ops/s
RedisIdGeneratorBenchmark.step_1000  thrpt       35369730.408          ops/s
```
