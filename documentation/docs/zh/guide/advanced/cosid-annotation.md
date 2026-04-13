# CosId 注解支持

CosId 提供注解支持，用于在领域对象中自动生成和注入 ID。

## @CosId 注解

`@CosId` 注解用于标记字段或类以进行 CosId ID 生成。

```java
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CosId {
    /**
     * 获取 ID 生成器名称。
     *
     * @return 生成器名称
     */
    String value() default IdGeneratorProvider.SHARE;

    /**
     * 获取 ID 字段名称（用于类型级注解）。
     *
     * @return 字段名称
     */
    String field() default DEFAULT_FIELD;
}
```

## 使用方式

### 字段级注解

直接在字段上使用 `@CosId` 标记：

```java
public class Order {
    @CosId
    private Long orderId;
    private Long userId;
}
```

### 类型级注解

标记类并指定字段名称：

```java
@CosId(value = "orderId")
public class Order {
    private Long orderId;
    private Long userId;
}
```

## CosIdAccessor

`CosIdAccessor` 提供对象的自动 ID 注入。它结合了多种能力：

- **CosIdGetter**: 从对象获取 ID 值
- **CosIdSetter**: 在对象上设置 ID 值
- **IdMetadata**: 提供 ID 字段的元数据
- **EnsureId**: 确保对象具有分配 ID

### 支持的 ID 类型

CosId 支持以下 ID 类型：
- `String`
- `Long` / `long`
- `Integer` / `int`

## 与 MyBatis 集成

CosId-MyBatis 集成在插入实体时自动注入 ID：

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

当调用 `orderRepository.insert(order)` 时，ID 会自动生成并设置。

## 解析器实现

CosId 使用 `CosIdAccessorParser` 解析 `@CosId` 注解并创建用于处理 ID 生成和注入的 `CosIdAccessor` 实例。
