# Setting Up CosId Snowflake ID Generation in a Pure Java/Maven Project

## 1. Add the Maven Dependency

CosId's `cosid-core` module has zero external dependencies (only `jspecify` annotations for null-safety), making it ideal for non-Spring projects.

```xml
<dependency>
    <groupId>me.ahoo.cosid</groupId>
    <artifactId>cosid-core</artifactId>
    <version>3.0.5</version>
</dependency>
```

If you want to manage all CosId module versions together, you can also import the BOM:

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
```

## 2. Create and Use the Snowflake ID Generator

The simplest setup is a single line. `MillisecondSnowflakeId` has a constructor that takes just the `machineId`:

```java
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;

public class IdGeneratorExample {
    public static void main(String[] args) {
        // Create a millisecond-precision snowflake ID generator with machineId=1
        MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(1);

        // Optionally wrap with ClockSyncSnowflakeId to handle clock drift gracefully
        ClockSyncSnowflakeId idGenerator = new ClockSyncSnowflakeId(snowflakeId);

        // Generate IDs
        long id1 = idGenerator.generate();
        long id2 = idGenerator.generate();

        System.out.println("ID 1 (long): " + id1);
        System.out.println("ID 2 (long): " + id2);

        // Generate as compact radix-62 string (default converter)
        String strId1 = idGenerator.generateAsString();
        System.out.println("ID 1 (string): " + strId1);
    }
}
```

## 3. What Is Happening Under the Hood

The default `MillisecondSnowflakeId(int machineId)` constructor uses these defaults:

| Parameter | Value | Meaning |
|---|---|---|
| Epoch | `1577203200000` (Dec 24, 2019 16:00 UTC) | Base timestamp for the CosId library |
| Timestamp bits | 41 | ~69 years of millisecond timestamps from the epoch |
| Machine bits | 10 | Supports up to 1024 unique machines |
| Sequence bits | 12 | Up to 4096 IDs per millisecond per machine |

The bit layout of each 63-bit (positive long) ID is:

```
| 1 bit (sign) | 41 bits (timestamp) | 10 bits (machineId) | 12 bits (sequence) |
```

## 4. ClockSyncSnowflakeId (Recommended)

Wrap the generator with `ClockSyncSnowflakeId` to automatically handle clock drift scenarios. Without it, a `ClockBackwardsException` will be thrown if the system clock moves backwards. The wrapper catches that exception and synchronizes the clock before retrying.

## 5. Custom Bit Configuration (Optional)

If you need to adjust the bit allocation (e.g., fewer machines but more IDs per millisecond):

```java
import me.ahoo.cosid.CosId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;

// 41 timestamp bits, 5 machine bits (32 machines), 17 sequence bits (131072 IDs/ms)
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(
    CosId.COSID_EPOCH,  // epoch
    41,                  // timestampBit
    5,                   // machineBit
    17,                  // sequenceBit
    1                    // machineId
);
```

Note: `timestampBit + machineBit + sequenceBit` must equal 62 (63 total bits minus the sign bit is checked implicitly by the max values).

## 6. String ID Conversion

By default, `generateAsString()` uses `Radix62IdConverter.PAD_START`, which produces 11-character fixed-width strings using `[0-9A-Za-z]`. You can customize this:

```java
import me.ahoo.cosid.snowflake.StringSnowflakeId;
import me.ahoo.cosid.converter.Radix62IdConverter;

// Without padding (variable-length strings)
StringSnowflakeId stringIdGen = new StringSnowflakeId(snowflakeId, Radix62IdConverter.INSTANCE);
String id = stringIdGen.generateAsString();

// With padding (fixed 11-char strings, default)
StringSnowflakeId stringIdGen = new StringSnowflakeId(snowflakeId, Radix62IdConverter.PAD_START);
```

## 7. Complete Minimal Example

```java
package com.example;

import me.ahoo.cosid.snowflake.ClockSyncSnowflakeId;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;

public class DistributedIdApp {
    private final SnowflakeId idGenerator;

    public DistributedIdApp(int machineId) {
        this.idGenerator = new ClockSyncSnowflakeId(
            new MillisecondSnowflakeId(machineId)
        );
    }

    public long nextId() {
        return idGenerator.generate();
    }

    public String nextIdAsString() {
        return idGenerator.generateAsString();
    }

    public static void main(String[] args) {
        DistributedIdApp app = new DistributedIdApp(1);

        for (int i = 0; i < 10; i++) {
            System.out.println(app.nextId() + " -> " + app.nextIdAsString());
        }
    }
}
```

This setup requires only `cosid-core` -- no Spring, no Redis, no database. The `MillisecondSnowflakeId` generator is thread-safe and can produce over 15 million IDs per second per instance.
