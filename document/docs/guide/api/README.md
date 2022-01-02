# IdGenerator

> **分布式ID**生成器

```java
@ThreadSafe
public interface IdGenerator {

    /**
     * ID converter, used to convert {@link long} type ID to {@link String}
     *
     * @return ID converter
     */
    default IdConverter idConverter() {
        return ToStringIdConverter.INSTANCE;
    }

    /**
     * Generate distributed ID
     *
     * @return distributed ID
     */
    long generate();

    /**
     * Generate distributed ID as String
     *
     * @return distributed ID as String
     */
    default String generateAsString() {
        return idConverter().asString(generate());
    }
}
```

## IdConverter

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

## CosIdAnnotationSupport
