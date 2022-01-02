# IdConverter

TODO

> **ID转换器**，用于将 `long` 类型ID转换为 `String`，反之亦然。

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

## ToStringIdConverter

## Radix62IdConverter

## SnowflakeFriendlyIdConverter

## PrefixIdConverter
