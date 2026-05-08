# Converting CosId Snowflake IDs to Short Alphanumeric Strings with a Date Prefix

CosId provides built-in converters for exactly this use case. You need two things:

1. **`Radix62IdConverter`** -- encodes numeric IDs into compact base-62 alphanumeric strings (0-9, A-Z, a-z).
2. **`DatePrefixIdConverter`** -- a decorator that prepends a formatted date prefix (with a delimiter) to the output of another converter.

## Step 1: Radix62 Encoding (the compact alphanumeric part)

`Radix62IdConverter` converts a `long` ID into a short string using 62 characters (0-9, A-Z, a-z). For example:

- `12345` becomes `"3d7"`
- `123456789` becomes `"8M0kX"`
- `9876543210L` becomes `"1Zi6jE"`

You can use it standalone:

```java
import me.ahoo.cosid.converter.Radix62IdConverter;

IdConverter converter = Radix62IdConverter.PAD_START; // fixed-width (11 chars)
String idStr = converter.asString(123456789L);  // "0000008M0kX"

long id = converter.asLong("0000008M0kX");      // 123456789
```

Or without padding for the shortest possible string:

```java
IdConverter converter = Radix62IdConverter.INSTANCE; // variable-length
String idStr = converter.asString(123456789L);  // "8M0kX"
```

## Step 2: Adding a Date Prefix

Wrap the radix converter with `DatePrefixIdConverter`, which takes a date format pattern, a delimiter, and the inner converter:

```java
import me.ahoo.cosid.converter.DatePrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;

// Pattern "yyMMdd" gives you "240601" for June 1, 2024
// Delimiter "-" separates the date from the encoded ID
IdConverter converter = new DatePrefixIdConverter(
    "yyMMdd",                       // date format pattern
    "-",                            // delimiter between date and ID
    Radix62IdConverter.PAD_START    // the inner radix-62 converter
);

String idStr = converter.asString(123456789L);
// Result on June 1, 2024: "240601-0000008M0kX"

long id = converter.asLong("240601-0000008M0kX");
// Result: 123456789
```

The `DatePrefixIdConverter` knows how to strip the prefix during `asLong()` by skipping characters equal to the pattern length plus delimiter length.

## Complete Example with a SnowflakeId Generator

```java
import me.ahoo.cosid.IdConverter;
import me.ahoo.cosid.converter.DatePrefixIdConverter;
import me.ahoo.cosid.converter.Radix62IdConverter;
import me.ahoo.cosid.snowflake.MillisecondSnowflakeId;
import me.ahoo.cosid.snowflake.SnowflakeId;

// Create the converter chain: date prefix + radix-62
IdConverter converter = new DatePrefixIdConverter(
    "yyMMdd",
    "-",
    Radix62IdConverter.PAD_START
);

// Create a snowflake ID generator with epoch starting Jan 1, 2024
SnowflakeId snowflakeId = new MillisecondSnowflakeId(
    1704067200000L,  // epoch (2024-01-01T00:00:00Z in millis)
    1,               // machineId
    converter
);

// Generate IDs
long id = snowflakeId.generate();
String idStr = snowflakeId.generateAsString();
// Example result: "240601-0000003d7Fg"
```

## Key Points

- **Radix62IdConverter.PAD_START** produces fixed-width 11-character strings (padded with `0`), which is recommended for database sorting and consistent storage.
- **Radix62IdConverter.INSTANCE** (no padding) produces variable-length strings -- shorter but less sort-friendly.
- **DatePrefixIdConverter** is a decorator: it wraps any `IdConverter` and prepends the current date on `asString()`, then strips it on `asLong()`.
- The date prefix is computed from `LocalDateTime.now()` at conversion time, so it reflects when the ID was converted, not necessarily the timestamp embedded in the snowflake ID.
- If you are using these string IDs as database primary keys, ensure the column uses a case-sensitive collation, since Radix62 includes both uppercase and lowercase letters.
