# Converting CosId Snowflake IDs to Short Alphanumeric Strings with a Date Prefix

CosId provides two building blocks that solve your exact need: **`Radix62IdConverter`** for compact alphanumeric encoding and **`DatePrefixIdConverter`** for prepending a date-based prefix. Here is how to use them together.

---

## 1. Adding the Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation(platform("me.ahoo.cosid:cosid-bom"))
    implementation("me.ahoo.cosid:cosid-core")
}
```

## 2. Convert a long ID to a Compact Alphanumeric String

`Radix62IdConverter` encodes a `long` into a base-62 string using characters `0-9`, `A-Z`, `a-z`. The static instance `Radix62IdConverter.PAD_START` pads to a fixed 11-character width (the max needed for any `long` value):

```java
import me.ahoo.cosid.converter.Radix62IdConverter;

IdConverter converter = Radix62IdConverter.PAD_START;
String compact = converter.asString(123456789L);
// Result: "0001ly7VK"
```

If you prefer variable-length (no padding), use `Radix62IdConverter.INSTANCE`:

```java
String compact = Radix62IdConverter.INSTANCE.asString(123456789L);
// Result: "1ly7VK"
```

## 3. Adding a Date Prefix (e.g. "240601-")

Use `DatePrefixIdConverter`, which takes a date format pattern, a delimiter, and an inner converter:

```java
import me.ahoo.cosid.converter.DatePrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;

DatePrefixIdConverter converter = new DatePrefixIdConverter(
    "yyMMdd",                              // date pattern
    "-",                                   // delimiter
    Radix62IdConverter.INSTANCE            // inner converter for the ID part
);

String result = converter.asString(123456789L);
// Example result: "260508-1ly7VK"
//                  ^^^^^^   ^^^^^^
//                  date      radix62-encoded ID
```

The date prefix uses `LocalDateTime.now()` at the moment of conversion, formatted with your pattern. The `asLong()` method strips the prefix and parses the ID back:

```java
long id = converter.asLong("260508-1ly7VK");
// Returns: 123456789
```

## 4. Full Integration with SnowflakeId

To generate snowflake IDs that are automatically converted to date-prefixed compact strings, wrap your `SnowflakeId` with `StringSnowflakeId`:

```java
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.StringSnowflakeId;
import me.ahoo.cosid.converter.DatePrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;

// 1. Create the SnowflakeId generator
int machineId = 1;
MillisecondSnowflakeId snowflakeId = new MillisecondSnowflakeId(machineId);

// 2. Create the converter: date prefix + radix62
DatePrefixIdConverter converter = new DatePrefixIdConverter(
    "yyMMdd", "-", Radix62IdConverter.INSTANCE
);

// 3. Wrap with StringSnowflakeId
StringSnowflakeId stringId = new StringSnowflakeId(snowflakeId, converter);

// 4. Generate IDs
String id = stringId.generateAsString();
// Example: "260508-00Fj8V0eXQ6"
```

## 5. Summary of Key Classes

| Class | Purpose |
|---|---|
| `Radix62IdConverter.PAD_START` | Fixed-width (11 chars) base-62 encoding |
| `Radix62IdConverter.INSTANCE` | Variable-length base-62 encoding |
| `DatePrefixIdConverter` | Prepends a date-formatted prefix + delimiter |
| `PrefixIdConverter` | Prepends a static string prefix (no date logic) |
| `StringSnowflakeId` | Decorator that wraps `SnowflakeId` + `IdConverter` |

All `IdConverter` and `IdGenerator` implementations in CosId are thread-safe (`@ThreadSafe`), so a single instance can be shared across threads without synchronization.
