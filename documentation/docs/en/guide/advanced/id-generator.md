# IdGenerator

> **Distributed ID** generator

## Interface Definition

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

## IdGenerator Implementation Class Diagram

<p align="center">
  <img src="../../../public/assets/design/IdGenerator-impl-class.png" alt="IdGenerator implementation class diagram"/>
</p>

## Implementations

CosId provides several `IdGenerator` implementations:

| Implementation | Description |
|----------------|-------------|
| `CosIdGenerator` | Universal, high-performance generator supporting million-scale instances |
| `SnowflakeId` | Twitter Snowflake algorithm implementation |
| `SegmentId` | Segment-based ID generation with batch allocation |
| `SegmentChainId` | Lock-free segment chain with prefetch worker |

## Usage Example

```java
// Inject via @Autowired
@Autowired
private IdGenerator idGenerator;

// Generate long ID
long id = idGenerator.generate();

// Generate String ID
String idStr = idGenerator.generateAsString();
```
