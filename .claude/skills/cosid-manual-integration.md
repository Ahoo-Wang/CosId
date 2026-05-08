---
name: cosid-manual-integration
description: Guide for integrating CosId via Java API in non-Spring Boot projects. Use when users mention manual configuration, Java API, non-Spring, pure Java, or need to create SnowflakeId/SegmentId/SegmentChainId programmatically.
---

# CosId 手动集成指南（非 Spring Boot）

TRIGGER: 用户在非 Spring Boot 项目中使用 CosId Java API（关键词：手动配置、Java API、非 Spring、纯 Java、程序化创建）

## 1. 添加依赖

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-core")
}
```

### 可选的 Distributor 依赖

根据需要的 Segment 分配后端，按需添加：

```kotlin
// Redis 分配器
implementation("me.ahoo.cosid:cosid-spring-redis")

// JDBC 分配器
implementation("me.ahoo.cosid:cosid-jdbc")

// MongoDB 分配器
implementation("me.ahoo.cosid:cosid-mongo")

// ZooKeeper 分配器
implementation("me.ahoo.cosid:cosid-zookeeper")
```

### Maven

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-bom</artifactId>
            <version>${cosid.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>me.ahoo.cosid</groupId>
    <artifactId>cosid-core</artifactId>
</dependency>
```

## 2. SnowflakeId 创建

### 基础用法（单机场景）

```java
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.IdGenerator;

// 使用固定 machineId（单机场景）
int machineId = 1;
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);

long id = snowflakeId.generate();
// 1702345678901234567
```

### 自定义位分配

```java
import me.ahoo.cosid.CosId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

// 41 位时间戳 + 5 位机器 ID + 17 位序列号（更高单机吞吐）
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(
    CosId.COSID_EPOCH,    // epoch 起点（2019-12-24）
    41,                    // timestampBit
    5,                     // machineBit（最多 32 台机器）
    17,                    // sequenceBit（每毫秒 131072 个 ID）
    machineId,
    SnowflakeId.defaultSequenceResetThreshold(17)
);
```

### SecondSnowflakeId（秒级精度）

```java
import me.ahoo.cosid.snowflake.SecondSnowflakeId;

SecondSnowflakeId snowflakeId = new SecondSnowflakeId(
    1577203200,  // epoch（秒级时间戳）
    31,           // timestampBit（秒级可用约 68 年）
    10,           // machineBit
    22,           // sequenceBit（每秒 4194304 个 ID）
    machineId,
    SecondSnowflakeId.defaultSequenceResetThreshold(22)
);
```

### 带 IdConverter 的字符串 ID

```java
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.snowflake.StringSnowflakeId;

MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);
StringSnowflakeId stringId = new StringSnowflakeId(snowflakeId, Radix62IdConverter.PAD_START);

String id = stringId.generateAsString();
// "00Fj8V0eXQ6"
```

## 3. SegmentId / SegmentChainId 创建

### SegmentChainId（推荐，高性能）

```java
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;

// 1. 创建 Distributor（以 Redis 为例）
IdSegmentDistributor distributor = new SpringRedisIdSegmentDistributor(redisTemplate);

// 2. 创建 SegmentChainId（推荐使用单参数构造函数，自动使用默认配置）
SegmentChainId segmentChainId = new SegmentChainId(distributor);

long id = segmentChainId.generate();

// 如需自定义 ttl 和 safeDistance，使用 4 参数构造函数：
// SegmentChainId segmentChainId = new SegmentChainId(
//     TIME_TO_LIVE_FOREVER,  // idSegmentTtl（段过期时间，-1 表示永不过期）
//     10,                     // safeDistance（预取安全距离）
//     distributor,
//     new PrefetchWorkerExecutorService()
// );
```

### SegmentId（基础模式）

```java
import me.ahoo.cosid.segment.DefaultSegmentId;

DefaultSegmentId segmentId = new DefaultSegmentId(distributor);

long id = segmentId.generate();
```

## 4. IdConverter 使用

CosId 提供多种 ID 转换器，将 long 型 ID 转为字符串：

```java
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.Radix36IdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;
import me.ahoo.cosid.converter.PrefixIdConverter;
import me.ahoo.cosid.converter.SuffixIdConverter;
import me.ahoo.cosid.converter.DatePrefixIdConverter;

// Radix62（默认，推荐）：紧凑的字母数字编码
IdConverter converter = Radix62IdConverter.PAD_START;
converter.asString(123456789L); // "1ly7VK"

// Radix36：大写字母数字编码
IdConverter converter36 = Radix36IdConverter.PAD_START;

// ToString：直接转字符串
IdConverter toStringConv = ToStringIdConverter.INSTANCE;

// 前缀/后缀装饰器
IdConverter prefixed = new PrefixIdConverter("ORD-", Radix62IdConverter.PAD_START);
prefixed.asString(123456789L); // "ORD-1ly7VK"

IdConverter suffixed = new SuffixIdConverter("-BIZ", ToStringIdConverter.INSTANCE);
```

### DatePrefixIdConverter：带日期前缀的 ID

`DatePrefixIdConverter` 是一个装饰器，在 ID 前添加基于当前日期的前缀。适合需要按日期归档或排序的场景：

```java
import me.ahoo.cosid.converter.DatePrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;

// 示例 1：日期前缀 + Radix62 编码
// 输出格式："240601-1ly7VK"（日期 + 连字符 + 编码）
IdConverter datePrefix = new DatePrefixIdConverter(
    "yyMMdd",              // 日期模式
    "-",                   // 分隔符
    Radix62IdConverter.PAD_START  // 内部转换器
);

// 示例 2：日期前缀 + 数字字符串（适合订单号）
// 输出格式："20240601-0000001234"
IdConverter datePrefixNum = new DatePrefixIdConverter(
    "yyyyMMdd",            // 日期模式
    "-",                   // 分隔符
    ToStringIdConverter.INSTANCE
);

// 示例 3：日期前缀 + 业务前缀（三重装饰器组合）
// 输出格式："ORD-240601-1ly7VK"
IdConverter fullPrefix = new PrefixIdConverter(
    "ORD-",
    new DatePrefixIdConverter("yyMMdd", "-", Radix62IdConverter.PAD_START)
);

// 与 SnowflakeId 配合使用
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);
StringSnowflakeId stringId = new StringSnowflakeId(snowflakeId, datePrefix);
String id = stringId.generateAsString();
// "240601-00Fj8V0eXQ6"
```

常用日期模式：
- `yyMMdd` → `240601`（简短，适合短 ID）
- `yyyyMMdd` → `20240601`（完整日期）
- `yyMM` → `2406`（按月归档）
- `yy` → `24`（按年归档）

## 5. MachineIdDistributor 手动配置（要点提示）

分布式环境下需要通过 `MachineIdDistributor` 分配唯一的 MachineId：

```java
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.InstanceId;

// Redis 实现
MachineIdDistributor distributor = new SpringRedisMachineIdDistributor(redisTemplate, Duration.ofSeconds(10));

// 分配 MachineId
InstanceId instanceId = InstanceId.of("my-host", 8080);
int machineId = distributor.distribute("my-namespace", 0, instanceId);

// 使用分配到的 machineId 创建 SnowflakeId
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);
```

其他后端实现：
- `JdbcMachineIdDistributor`（cosid-jdbc）
- `ZooKeeperMachineIdDistributor`（cosid-zookeeper）
- `MongoMachineIdDistributor`（cosid-mongo）
- `ManualMachineIdDistributor`（cosid-core，手动指定固定 ID）
- `StatefulSetMachineIdDistributor`（cosid-core，K8s StatefulSet 场景）

## 6. CosIdGenerator（要点提示）

独立高性能 ID 生成器，不依赖任何后端服务：

```java
import me.ahoo.cosid.cosid.CosIdGenerator;

CosIdGenerator generator = CosIdGenerator.INSTANCE;
long id = generator.generate();
// 性能：约 15M+ ops/s（单线程）
```

## 7. 常见问题

- **线程安全：** 所有 IdGenerator 实现都是线程安全的（`@ThreadSafe`），无需额外同步。
- **生命周期管理：** 使用 SegmentChainId 时，`PrefetchWorkerExecutorService` 需要在应用关闭时调用 `shutdown()` 释放线程资源。MachineIdDistributor 的 guard 心跳也需要关闭。
- **MachineId 范围：** 默认 10 位 machineBit（0-1023），确保每台机器使用不同的 machineId。
- **epoch 设计：** 选择合适的 epoch 起始时间，确保 41 位时间戳在预期使用年限内不会溢出（约 69 年）。
