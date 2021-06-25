# [CosId](https://github.com/Ahoo-Wang/CosId) 通用、灵活、高性能的分布式 ID 生成器

## 介绍

*[CosId](https://github.com/Ahoo-Wang/CosId)* 全局分布式 ID 生成器，旨在提供通用、灵活、高性能的分布式系统 ID 生成器。 目前提供了俩大类 ID 生成器：*SnowflakeId* （单机
TPS 性能：409W [JMH 基准测试](#jmh-benchmark)）、*RedisIdGenerator* (单机 TPS 性能(步长 1000)：1268W [JMH 基准测试](#jmh-benchmark))。

## SnowflakeId

> *SnowflakeId* 使用 `Long` （64 bits） 位分区来生成 ID 的一种分布式 ID 算法。
> 通用位分配方案为：`timestamp` (41 bits) + `machineId` (10 bits) + `sequence` (12 bits) = 63 bits 。

- 41 位 `timestamp` = (1L<<41)/(1000/3600/365) 约可以存储 69 年的时间戳，即可以使用的绝对时间为 `EPOCH` + 69 年，一般我们需要自定义`EPOCH`为产品启动时间。
- 10 位 `machineId` = (1L<<10) = 1024 即相同业务可以部署 1024 个副本 (在 Kubernetes 概念里没有主从副本之分，这里直接沿用 Kubernetes 的定义)
  实例，一般情况下没有必要使用这么多位，所以会根据部署规模需要重新定义。
- 12 位 `sequence` = (1L<<12) * 1000 = 4096000 即单机每秒可生成约 409W 的 ID，全局同业务集群可产生 4096000*1024=419430W=41.9亿(TPS)。

从 *SnowflakeId* 设计上可以看出:

- `timestamp` 在高位，所以 *SnowflakeId* 是本机单调递增的，受全局时钟同步影响 *SnowflakeId* 是全局趋势递增的。
- *SnowflakeId* 不对任何第三方中间件有强依赖关系，并且性能也非常高。
- 强依赖本机时钟，潜在的时钟回拨问题会导致 ID 重复。
- `machineId` 需要手动设置，实际部署时如果采用手动分配 `machineId`，会非常低效。

---

*[CosId-SnowflakeId](https://github.com/Ahoo-Wang/CosId)* 主要解决 *SnowflakeId* 俩大问题：机器号分配问题、时钟回拨问题。 并且提供更加友好、灵活的使用体验。

### MachineIdDistributor (MachineId 分配器)

#### ManualMachineIdDistributor

> 手动分配 `MachineId`

#### StatefulSetMachineIdDistributor

> 使用 `Kubernetes` 的 `StatefulSet` 提供的稳定的标识 ID 作为机器号

#### RedisMachineIdDistributor

> 使用 `Redis` 作为机器号的分发存储

### ClockBackwardsSynchronizer (时钟回拨同步器)

### LocalMachineState (本地机器状态存储)

### ClockSyncSnowflakeId (主动时钟同步 `SnowflakeId`)

默认 `SnowflakeId` 当发生时钟回拨时会直接抛出 `ClockBackwardsException` 异常，而使用 `ClockSyncSnowflakeId` 会使用 `ClockBackwardsSynchronizer`
主动等待时钟同步来重新生成 ID，提高更加友好的使用体验。

### SafeJavaScriptSnowflakeId (`JavaScript` 安全的 `SnowflakeId`)

### SnowflakeIdStateParser (可以将 `SnowflakeId` 解析成可读性更好的 `SnowflakeIdState` )

```java
        SnowflakeIdState idState=snowflakeIdStateParser.parse(id);
        idState.getFriendlyId(); //20210623131730192-1-0
```

## RedisIdGenerator

通过自定义步长来降低网络IO请求，提升TPS性能。

## IdGeneratorProvider

[CosId-Examples](https://github.com/Ahoo-Wang/CosId/tree/main/cosid-example)

## 安装

### Gradle

> Kotlin DSL

``` kotlin
    val cosidVersion = "0.8.0";
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
        <cosid.version>0.8.0</cosid.version>
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
  #  stateful-set:
  #    enabled: true
  #  manual:
  #    enabled: true
  #    machine-id: 1
  redis:
    enabled: true
  providers:
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
```

## IdGenerator

```java
        IdGenerator idGen=new MillisecondSnowflakeId(1);
        long id=idGen.generate();

        MillisecondSnowflakeIdStateParser snowflakeIdStateParser=MillisecondSnowflakeIdStateParser.of(idGen);
        SnowflakeIdState idState=snowflakeIdStateParser.parse(id);
        idState.getFriendlyId(); //20210623131730192-1-0

```

### SafeJavaScriptSnowflakeId

```java
    IdGenerator snowflakeId=SafeJavaScriptSnowflakeId.ofMillisecond(1);
```

## MachineIdDistributor

### StatefulSetMachineIdDistributor (On Kubernetes)

```yaml
cosid:
  stateful-set:
    enabled: true
```

### ManualMachineIdDistributor

```yaml
cosid:
  manual:
    enabled: true
    machine-id: 1
```

### RedisMachineIdDistributor

> Support clock callback verification, and wait until it catches up with the clock callback.

``` kotlin
    val cosidVersion = "0.8.0";
    implementation("me.ahoo.cosid:cosid-redis:${cosidVersion}")
```

```yaml
cosid:
  redis:
    enabled: true
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

```
Benchmark                              Mode  Cnt         Score   Error  Units
RedisIdGeneratorBenchmark.step_1      thrpt          86243.935          ops/s
RedisIdGeneratorBenchmark.step_100    thrpt        1718229.010          ops/s
RedisIdGeneratorBenchmark.step_1000   thrpt       12688174.755          ops/s
RedisIdGeneratorBenchmark.step_10000  thrpt       13995195.387          ops/s
```
