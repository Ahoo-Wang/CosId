# CosId Annotation Support

CosId provides annotation support for automatic ID generation and injection in your domain objects.

## @CosId Annotation

The `@CosId` annotation marks fields or classes for CosId ID generation.

```java
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CosId {
    /**
     * Gets the ID generator name.
     *
     * @return the generator name
     */
    String value() default IdGeneratorProvider.SHARE;

    /**
     * Gets the ID field name (for type-level annotation).
     *
     * @return the field name
     */
    String field() default DEFAULT_FIELD;
}
```

## Usage

### Field-Level Annotation

Mark a field directly with `@CosId`:

```java
public class Order {
    @CosId
    private Long orderId;
    private Long userId;
}
```

### Type-Level Annotation

Mark a class and specify the field name:

```java
@CosId(value = "orderId")
public class Order {
    private Long orderId;
    private Long userId;
}
```

## CosIdAccessor

`CosIdAccessor` provides automatic ID injection for objects. It combines multiple capabilities:

- **CosIdGetter**: Getting ID values from objects
- **CosIdSetter**: Setting ID values on objects
- **IdMetadata**: Providing metadata about ID fields
- **EnsureId**: Ensuring objects have IDs assigned

### Supported ID Types

CosId supports the following ID types:
- `String`
- `Long` / `long`
- `Integer` / `int`

## Integration with MyBatis

CosId-MyBatis integration automatically injects IDs when inserting entities:

```java
@Mapper
public interface OrderRepository {
    @Insert("insert into t_table (id) value (#{id});")
    void insert(Order order);
}

public class Order {
    @CosId(value = "order")
    private Long orderId;
}
```

When `orderRepository.insert(order)` is called, the ID is automatically generated and set.

## Parser Implementation

CosId uses `CosIdAccessorParser` to parse `@CosId` annotations and create `CosIdAccessor` instances that handle ID generation and injection.
