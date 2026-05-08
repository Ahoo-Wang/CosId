# Converting CosId Snowflake IDs to Short Alphanumeric Strings with Date Prefix

CosId provides built-in `IdConverter` decorators that handle exactly this. You need two things:

1. **Radix62IdConverter** -- converts a `long` ID into a compact base-62 alphanumeric string (characters: `0-9`, `A-Z`, `a-z`).
2. **DatePrefixIdConverter** -- decorates any converter by prepending a formatted date prefix with a separator.

## Dependencies

```kotlin
// Gradle (Kotlin DSL)
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-core")
}
```

Or Maven:

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

## Solution

```java
import me.ahoo.cosid.converter.DatePrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.StringSnowflakeId;

// 1. Create the SnowflakeId generator (use a fixed machineId for single-node)
int machineId = 1;
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);

// 2. Build the converter: Radix62 encoding + date prefix "yyMMdd-"
//    This produces output like: "240601-8M0kX"
IdConverter datePrefixConverter = new DatePrefixIdConverter(
    "yyMMdd",                    // date pattern: short year + month + day
    "-",                         // delimiter between date and encoded ID
    Radix62IdConverter.PAD_START // inner converter: base-62, padded to 11 chars
);

// 3. Wrap the SnowflakeId with the converter
StringSnowflakeId stringId = new StringSnowflakeId(snowflakeId, datePrefixConverter);

// 4. Generate IDs
String id = stringId.generateAsString();
// Example output: "240601-000008M0kX"
//                  ^^^^^^^  ^^^^^^^^^
//                  date     Radix62-encoded Snowflake ID (11 chars)
```

## How It Works

**Radix62 encoding** converts a `long` to a compact string using 62 characters (`0-9`, `A-Z`, `a-z`). A full 63-bit Snowflake ID fits in just 11 characters -- much shorter than the 19-character decimal representation.

Concrete examples for `123456789`:

| Converter | Output |
|-----------|--------|
| `Radix62IdConverter.INSTANCE` (no padding) | `8M0kX` (5 chars) |
| `Radix62IdConverter.PAD_START` (padded to 11) | `0000008M0kX` (11 chars) |
| `DatePrefixIdConverter("yyMMdd", "-", PAD_START)` | `240601-0000008M0kX` |

For a typical Snowflake ID like `1702345678901234567`, the padded Radix62 output is `21klCJwZjD5` (11 chars).

## Choosing the Right Configuration

**Shortest IDs (no padding):** Use `Radix62IdConverter.INSTANCE` if you do not need fixed-width output. Smaller IDs produce shorter strings.

```java
IdConverter shortConv = new DatePrefixIdConverter(
    "yyMMdd", "-", Radix62IdConverter.INSTANCE
);
// Output: "240601-8M0kX" (variable length)
```

**Fixed-width (padded):** Use `Radix62IdConverter.PAD_START` for consistent string length, which is better for database indexing and sorting.

```java
IdConverter fixedConv = new DatePrefixIdConverter(
    "yyMMdd", "-", Radix62IdConverter.PAD_START
);
// Output: "240601-0000008M0kX" (always 18 chars)
```

**Custom char size:** You can reduce padding width if your IDs will never reach the full 63-bit range.

```java
IdConverter customConv = Radix62IdConverter.of(true, 8); // padded to 8 chars
IdConverter withDate = new DatePrefixIdConverter("yyMMdd", "-", customConv);
```

**Common date patterns:**

| Pattern | Example | Use case |
|---------|---------|----------|
| `yyMMdd` | `240601` | Short IDs, daily partitioning |
| `yyyyMMdd` | `20240601` | Full date, clearer readability |
| `yyMM` | `2406` | Monthly archiving |
| `yy` | `24` | Yearly archiving |

**Adding a business prefix on top:**

```java
import me.ahoo.cosid.converter.PrefixIdConverter;

// Triple decoration: business prefix + date + Radix62
IdConverter fullConverter = new PrefixIdConverter(
    "ORD-",
    new DatePrefixIdConverter("yyMMdd", "-", Radix62IdConverter.PAD_START)
);
StringSnowflakeId stringId = new StringSnowflakeId(snowflakeId, fullConverter);
// Output: "ORD-240601-0000008M0kX"
```

## Important Notes

- All ID generators in CosId are **thread-safe** (`@ThreadSafe`). No external synchronization is needed.
- `DatePrefixIdConverter.asString()` uses `LocalDateTime.now()` at call time, so the date reflects when the ID was generated.
- `DatePrefixIdConverter` is **bidirectional** -- `asLong()` strips the date prefix and decodes the Radix62 portion back to a `long`.
- Radix62 output is **case-sensitive**. If you use string IDs as database primary keys, ensure the column collation is case-sensitive (e.g., `utf8_bin` in MySQL).
- For distributed environments with multiple nodes, use a `MachineIdDistributor` (Redis, JDBC, ZooKeeper, or MongoDB) instead of hardcoding `machineId`.

## Key Source Files

- `cosid-core/src/main/java/me/ahoo/cosid/converter/Radix62IdConverter.java`
- `cosid-core/src/main/java/me/ahoo/cosid/converter/DatePrefixIdConverter.java`
- `cosid-core/src/main/java/me/ahoo/cosid/converter/PrefixIdConverter.java`
- `cosid-core/src/main/java/me/ahoo/cosid/snowflake/StringSnowflakeId.java`
- `cosid-core/src/main/java/me/ahoo/cosid/snowflake/MillisecondSnowflakeId.java`
