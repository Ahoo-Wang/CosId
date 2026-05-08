---
name: cosid-manual-integration
description: Guide for integrating CosId via Java API in non-Spring Boot projects. Use when users mention manual configuration, Java API, non-Spring, pure Java, or need to create SnowflakeId/SegmentId/SegmentChainId programmatically.
---

# CosId Manual Integration Guide (Non-Spring Boot)

TRIGGER: User is using CosId Java API in a non-Spring Boot project (keywords: manual configuration, Java API, non-Spring, pure Java, programmatic creation)

## 1. Add Dependencies

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-core")
}
```

### Optional Distributor Dependencies

Add the distributor module for your Segment backend as needed:

```kotlin
// Redis distributor
implementation("me.ahoo.cosid:cosid-spring-redis")

// JDBC distributor
implementation("me.ahoo.cosid:cosid-jdbc")

// MongoDB distributor
implementation("me.ahoo.cosid:cosid-mongo")

// ZooKeeper distributor
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

## 2. Creating a SnowflakeId

### Basic Usage (Single-Node)

```java
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.IdGenerator;

// Use a fixed machineId (single-node scenario)
int machineId = 1;
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);

long id = snowflakeId.generate();
// 1702345678901234567
```

### Custom Bit Layout

```java
import me.ahoo.cosid.CosId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

// 41-bit timestamp + 5-bit machineId + 17-bit sequence (higher single-node throughput)
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(
    CosId.COSID_EPOCH,    // epoch start (2019-12-24)
    41,                    // timestampBit
    5,                     // machineBit (up to 32 machines)
    17,                    // sequenceBit (131072 IDs per millisecond)
    machineId,
    SnowflakeId.defaultSequenceResetThreshold(17)
);
```

### SecondSnowflakeId (Second Precision)

```java
import me.ahoo.cosid.snowflake.SecondSnowflakeId;

SecondSnowflakeId snowflakeId = new SecondSnowflakeId(
    1577203200,  // epoch (seconds timestamp)
    31,           // timestampBit (~68 years at second precision)
    10,           // machineBit
    22,           // sequenceBit (4194304 IDs per second)
    machineId,
    SecondSnowflakeId.defaultSequenceResetThreshold(22)
);
```

### String IDs with IdConverter

```java
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.snowflake.StringSnowflakeId;

MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);
StringSnowflakeId stringId = new StringSnowflakeId(snowflakeId, Radix62IdConverter.PAD_START);

String id = stringId.generateAsString();
// "00Fj8V0eXQ6"
```

## 3. Creating SegmentId / SegmentChainId

### SegmentChainId (Recommended, High-Performance)

```java
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.IdSegmentDistributor;
import me.ahoo.cosid.segment.concurrent.PrefetchWorkerExecutorService;

// 1. Create a Distributor (Redis example)
IdSegmentDistributor distributor = new SpringRedisIdSegmentDistributor(redisTemplate);

// 2. Create SegmentChainId (single-arg constructor uses default config)
SegmentChainId segmentChainId = new SegmentChainId(distributor);

long id = segmentChainId.generate();

// For custom ttl and safeDistance, use the 4-arg constructor:
// SegmentChainId segmentChainId = new SegmentChainId(
//     TIME_TO_LIVE_FOREVER,  // idSegmentTtl (-1 = never expires)
//     10,                     // safeDistance (prefetch safety distance)
//     distributor,
//     new PrefetchWorkerExecutorService()
// );
```

### SegmentId (Basic Mode)

```java
import me.ahoo.cosid.segment.DefaultSegmentId;

DefaultSegmentId segmentId = new DefaultSegmentId(distributor);

long id = segmentId.generate();
```

## 4. IdConverter Usage

CosId provides multiple ID converters to transform long IDs into strings:

```java
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.Radix36IdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;
import me.ahoo.cosid.converter.PrefixIdConverter;
import me.ahoo.cosid.converter.SuffixIdConverter;
import me.ahoo.cosid.converter.DatePrefixIdConverter;

// Radix62 (default, recommended): compact alphanumeric encoding
IdConverter converter = Radix62IdConverter.PAD_START;
converter.asString(123456789L); // "1ly7VK"

// Radix36: uppercase alphanumeric encoding
IdConverter converter36 = Radix36IdConverter.PAD_START;

// ToString: direct string conversion
IdConverter toStringConv = ToStringIdConverter.INSTANCE;

// Prefix/Suffix decorators
IdConverter prefixed = new PrefixIdConverter("ORD-", Radix62IdConverter.PAD_START);
prefixed.asString(123456789L); // "ORD-1ly7VK"

IdConverter suffixed = new SuffixIdConverter("-BIZ", ToStringIdConverter.INSTANCE);
```

### DatePrefixIdConverter: Date-Prefixed IDs

`DatePrefixIdConverter` is a decorator that prepends a date-based prefix to any ID. Useful for archiving or sorting by date:

```java
import me.ahoo.cosid.converter.DatePrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;

// Example 1: Date prefix + Radix62 encoding
// Output: "240601-1ly7VK" (date + delimiter + encoded ID)
IdConverter datePrefix = new DatePrefixIdConverter(
    "yyMMdd",              // date pattern
    "-",                   // delimiter
    Radix62IdConverter.PAD_START  // inner converter
);

// Example 2: Date prefix + numeric string (good for order numbers)
// Output: "20240601-0000001234"
IdConverter datePrefixNum = new DatePrefixIdConverter(
    "yyyyMMdd",            // date pattern
    "-",                   // delimiter
    ToStringIdConverter.INSTANCE
);

// Example 3: Triple decoration (business prefix + date + encoding)
// Output: "ORD-240601-1ly7VK"
IdConverter fullPrefix = new PrefixIdConverter(
    "ORD-",
    new DatePrefixIdConverter("yyMMdd", "-", Radix62IdConverter.PAD_START)
);

// Integrate with SnowflakeId
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);
StringSnowflakeId stringId = new StringSnowflakeId(snowflakeId, datePrefix);
String id = stringId.generateAsString();
// "240601-00Fj8V0eXQ6"
```

Common date patterns:
- `yyMMdd` -> `240601` (short, good for compact IDs)
- `yyyyMMdd` -> `20240601` (full date, clearer readability)
- `yyMM` -> `2406` (monthly archiving)
- `yy` -> `24` (yearly archiving)

## 5. MachineIdDistributor Manual Setup (Quick Reference)

In distributed environments, use `MachineIdDistributor` to allocate unique MachineIds:

```java
import me.ahoo.cosid.machine.MachineIdDistributor;
import me.ahoo.cosid.machine.InstanceId;

// Redis implementation
MachineIdDistributor distributor = new SpringRedisMachineIdDistributor(redisTemplate, Duration.ofSeconds(10));

// Distribute MachineId
InstanceId instanceId = InstanceId.of("my-host", 8080);
int machineId = distributor.distribute("my-namespace", 0, instanceId);

// Create SnowflakeId with the allocated machineId
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);
```

Other backend implementations:
- `JdbcMachineIdDistributor` (cosid-jdbc)
- `ZooKeeperMachineIdDistributor` (cosid-zookeeper)
- `MongoMachineIdDistributor` (cosid-mongo)
- `ManualMachineIdDistributor` (cosid-core, fixed ID assignment)
- `StatefulSetMachineIdDistributor` (cosid-core, K8s StatefulSet)

## 6. CosIdGenerator (Quick Reference)

Standalone high-performance ID generator, no backend required:

```java
import me.ahoo.cosid.cosid.CosIdGenerator;

CosIdGenerator generator = CosIdGenerator.INSTANCE;
long id = generator.generate();
// Performance: ~15M+ ops/s (single-thread)
```

## 7. Common Issues

- **Thread safety:** All IdGenerator implementations are thread-safe (`@ThreadSafe`). No external synchronization needed.
- **Lifecycle management:** When using SegmentChainId, call `shutdown()` on `PrefetchWorkerExecutorService` on application close. MachineIdDistributor guard heartbeats also need shutdown.
- **MachineId range:** Default 10-bit machineBit (0-1023). Ensure each machine uses a unique machineId.
- **Epoch design:** Choose an appropriate epoch start time. The 41-bit timestamp supports ~69 years from the epoch.
