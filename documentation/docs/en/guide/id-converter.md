# IdConverter

> **ID Converter**, used to convert `long` type ID to `String`, and vice versa.

```java
@ThreadSafe
public interface IdConverter {

    /**
     * convert {@link long} type ID to {@link String}
     *
     * @param id {@link long} type ID
     * @return {@link String} type ID
     */
    String asString(long id);

    /**
     * convert {@link String} type ID to {@link long}
     *
     * @param idString {@link String} type ID
     * @return {@link long} type ID
     */
    long asLong(String idString);
}
```

## IdConverter implementation class diagram

<p align="center">
  <img src="../../public/assets/design/IdConverter-impl-class.png" alt="IdGenerator implementation class diagram"/>
</p>

## ToStringIdConverter

> String converter, used to convert `long` to String or String to long type

- Rules
  - long to String: String.valueOf
  - String to long: Long.parseLong

## Radix62IdConverter

> Base-62 converter, used to convert `long` type to `base-62 string`, or `base-62 string` to `long` type

- Rules: `[0-9][A-Z][a-z]{11}`

## Radix36IdConverter

> Base-36 converter, used to convert `long` type to `base-36 string`, or `base-36 string` to `long` type

- Rules: `[0-9][A-Z]{13}`

## SnowflakeFriendlyIdConverter

> Snowflake ID converter, converts strings conforming to snowflake rules to long, or long to snowflake rule strings

## PrefixIdConverter

> Converts strings with prefixes to long, or long to strings with prefixes

- Rules
  - For example: prefix is `no_`, converter uses `ToStringIdConverter`, number `1` after conversion gets `no_1`, and vice versa.
