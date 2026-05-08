---
name: cosid-manual-integration
description: Guide for integrating CosId distributed ID generator via Java API in non-Spring Boot projects. Use this skill whenever the user mentions creating SnowflakeId, SegmentId, or SegmentChainId programmatically, manual ID generator setup, pure Java distributed IDs, or non-Spring ID generation — even if they just say "I need unique IDs in my Java app" without mentioning CosId. Also trigger when the user asks about Radix62 encoding, ID converters, or MachineIdDistributor setup without Spring.
---

# CosId Manual Integration Guide (Non-Spring Boot)

## When to Use This Skill

Use this skill when a developer needs to integrate CosId in a non-Spring Boot project — pure Java, Gradle/Maven, or any framework other than Spring Boot. The key scenarios:

- Creating SnowflakeId with a fixed machineId for single-node use
- Setting up SegmentChainId with a programmatic distributor
- Converting IDs with Radix62, date prefixes, or custom formats
- Allocating machineIds manually or via a non-Spring distributor

If the user IS using Spring Boot, use `cosid-spring-boot` instead.

## 1. Add Dependencies

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-core")
}
```

### Optional Distributor Dependencies

Add the distributor module for your Segment backend as needed. These are only required for SegmentId/SegmentChainId — SnowflakeId with a fixed machineId needs only `cosid-core`.

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

Why: For single-node or development scenarios, a fixed machineId is the simplest approach. Each server gets a unique machineId (0-1023 with default 10-bit layout). If you have multiple servers, assign different machineIds to each.

```java
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

int machineId = 1;
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);

long id = snowflakeId.generate();
// 1702345678901234567
```

### Custom Bit Layout

Why: The default 41+10+12 layout supports 1024 machines and 4096 IDs/ms. If you need fewer machines but more IDs per millisecond (e.g. a single powerful server), shift bits from machineId to sequence.

```java
import me.ahoo.cosid.CosId;
import me.ahoo.cosid.snowflake.SnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

// 41-bit timestamp + 5-bit machineId (32 machines) + 17-bit sequence (131072 IDs/ms)
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(
    CosId.COSID_EPOCH,    // epoch start (2019-12-24)
    41,                    // timestampBit
    5,                     // machineBit
    17,                    // sequenceBit
    machineId,
    SnowflakeId.defaultSequenceResetThreshold(17)
);
```

### SecondSnowflakeId (Second Precision)

Why: Millisecond precision uses 41 bits for ~69 years. If you can tolerate second precision, you get more bits for machineId and sequence — useful for very high throughput or many machines.

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

Why: SegmentChainId uses a lock-free linked list with asynchronous prefetching. It allocates IDs in batches from a backend (Redis, JDBC, etc.), reducing network round-trips. The chain mode dynamically adjusts prefetch distance based on consumption rate — this is why it achieves ~127M+ ops/s.

```java
import me.ahoo.cosid.segment.SegmentChainId;
import me.ahoo.cosid.segment.IdSegmentDistributor;

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

Why: Basic mode is simpler but has lower throughput. Each ID generation checks if the current segment is exhausted and fetches a new one synchronously. Use it when throughput is not critical.

```java
import me.ahoo.cosid.segment.DefaultSegmentId;

DefaultSegmentId segmentId = new DefaultSegmentId(distributor);

long id = segmentId.generate();
```

## 4. IdConverter Usage

Why: CosId generates numeric IDs internally, but you often need string representations for URLs, order numbers, or display. IdConverters handle this transformation and can be composed (prefix + date + encoding) for rich ID formats.

```java
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.converter.Radix36IdConverter;
import me.ahoo.cosid.converter.ToStringIdConverter;
import me.ahoo.cosid.converter.PrefixIdConverter;
import me.ahoo.cosid.converter.SuffixIdConverter;
import me.ahoo.cosid.converter.DatePrefixIdConverter;

// Radix62 (default, recommended): compact alphanumeric encoding
// Why Radix62: Uses 62 characters (0-9, A-Z, a-z), producing the shortest strings.
// A full 63-bit Snowflake ID becomes just 11 characters.
IdConverter converter = Radix62IdConverter.PAD_START;
converter.asString(123456789L); // "1ly7VK"

// Radix36: uppercase alphanumeric encoding
// Why Radix36: Case-insensitive, safer for systems that normalize case.
IdConverter converter36 = Radix36IdConverter.PAD_START;

// ToString: direct string conversion
IdConverter toStringConv = ToStringIdConverter.INSTANCE;

// Prefix/Suffix decorators
IdConverter prefixed = new PrefixIdConverter("ORD-", Radix62IdConverter.PAD_START);
prefixed.asString(123456789L); // "ORD-1ly7VK"

IdConverter suffixed = new SuffixIdConverter("-BIZ", ToStringIdConverter.INSTANCE);
```

### DatePrefixIdConverter: Date-Prefixed IDs

Why: Adding a date prefix enables natural time-based sorting and archiving. For example, `240601-` prefix means all IDs from June 1, 2024 sort together, making it easy to partition or query by date.

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
// Why compose decorators: Each decorator adds one concern (business context,
// time context, encoding format). Composing them keeps each piece simple.
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

Why: In distributed environments, each application instance needs a unique machineId. MachineIdDistributor coordinates this — it allocates, guards (via heartbeat), and reclaims machineIds. Without it, you must manually assign unique machineIds to each server.

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
- `StatefulSetMachineIdDistributor` (cosid-core, K8s StatefulSet — uses pod ordinal as machineId)

## 6. CosIdGenerator (Quick Reference)

Why: CosIdGenerator is a standalone ID generator that doesn't need any backend coordination. It encodes a timestamp + machineId + sequence into a single long. Use it when you want simplicity and don't need cross-instance coordination.

```java
import me.ahoo.cosid.cosid.CosIdGenerator;

CosIdGenerator generator = CosIdGenerator.INSTANCE;
long id = generator.generate();
// Performance: ~15M+ ops/s (single-thread)
```

## 7. Common Issues

- **Thread safety:** All IdGenerator implementations are thread-safe (`@ThreadSafe`). A single instance can be shared across all threads — no pooling or synchronization needed.
- **Lifecycle management:** SegmentChainId has a background prefetch worker. Call `shutdown()` on `PrefetchWorkerExecutorService` on application close to release threads. MachineIdDistributor guard heartbeats also need shutdown.
- **MachineId range:** Default 10-bit machineBit (0-1023). With more than 1024 instances, you need to increase machineBit (at the cost of fewer sequence bits).
- **Epoch design:** The default epoch is Dec 24, 2019. The 41-bit timestamp supports ~69 years from the epoch, so it's safe until ~2088. Don't change the epoch unless you have a specific reason.
