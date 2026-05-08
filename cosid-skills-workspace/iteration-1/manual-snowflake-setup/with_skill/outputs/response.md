# Setting Up CosId Snowflake (Millisecond Precision) in a Pure Java/Maven Project

## 1. Add Maven Dependency

CosId uses a BOM (Bill of Materials) to manage versions. Add it to your `pom.xml`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosid</groupId>
            <artifactId>cosid-bom</artifactId>
            <version>3.0.5</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>me.ahoo.cosid</groupId>
        <artifactId>cosid-core</artifactId>
    </dependency>
</dependencies>
```

`cosid-core` has no external runtime dependencies (only `jspecify` for null-safety annotations), so it is lightweight and suitable for any pure Java project.

## 2. Create the Snowflake ID Generator

With a fixed `machineId=1`, the setup is a single constructor call:

```java
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

public class IdGeneratorExample {
    public static void main(String[] args) {
        // machineId = 1 (each server gets a unique value in the range 0-1023)
        int machineId = 1;
        MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);

        // Generate IDs
        long id1 = snowflakeId.generate();
        long id2 = snowflakeId.generate();
        System.out.println(id1); // e.g., 1786543210987654321
        System.out.println(id2); // e.g., 1786543210987654322
    }
}
```

That is the minimal setup. The default constructor uses:
- **Epoch**: December 24, 2019 (CosId epoch) -- maximizes the usable range of the 41-bit timestamp
- **Bit layout**: 41 bits timestamp + 10 bits machine ID + 12 bits sequence = 63 bits total
- **Capacity**: up to 4,096 IDs per millisecond per machine, ~69 years of timestamps from the epoch

## 3. Getting String IDs (Optional)

If you prefer shorter, human-friendly string IDs (e.g., for URLs, order numbers):

```java
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.StringSnowflakeId;

MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(1);
StringSnowflakeId stringId = new StringSnowflakeId(snowflakeId, Radix62IdConverter.PAD_START);

String id = stringId.generateAsString();
// e.g., "00Fj8V0eXQ6"
```

You can also add a prefix:

```java
import me.ahoo.cosid.converter.PrefixIdConverter;

PrefixIdConverter prefixed = new PrefixIdConverter("ORD-", Radix62IdConverter.PAD_START);
StringSnowflakeId orderId = new StringSnowflakeId(snowflakeId, prefixed);
String id = orderId.generateAsString();
// e.g., "ORD-00Fj8V0eXQ6"
```

## 4. Custom Bit Layout (Optional)

If you need higher single-machine throughput (at the cost of fewer machines):

```java
import me.ahoo.cosid.CosId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;

// 41 bits timestamp + 5 bits machine (32 machines) + 17 bits sequence (131,072 IDs/ms)
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(
    CosId.COSID_EPOCH,     // epoch
    41,                     // timestampBit
    5,                      // machineBit
    17,                     // sequenceBit
    1,                      // machineId
    SnowflakeId.defaultSequenceResetThreshold(17)
);
```

## Key Points

- **Thread safety**: `MillisecondSnowflakeId` is `@ThreadSafe`. A single instance can be shared across all threads in your application -- no pooling or synchronization needed.
- **Machine ID uniqueness**: With a fixed `machineId=1` on every server, you must ensure no two servers share the same `machineId`. If you have multiple servers, assign each a different value (0-1023 with the default 10-bit machine field). For dynamic allocation, consider using a `MachineIdDistributor` (Redis, JDBC, ZooKeeper, or MongoDB backends are available).
- **Clock drift**: If the system clock moves backwards, `generate()` will throw a `ClockBackwardsException`. CosId provides `ClockBackwardsSynchronizer` for production deployments if NTP clock adjustments are a concern.
- **No lifecycle management needed**: Unlike `SegmentChainId` (which has a background prefetch worker), `MillisecondSnowflakeId` has no resources to shut down. Just create it and use it.
