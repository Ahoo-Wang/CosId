# @CosId 字段填充

`@CosId` 标记实体的 ID 字段；`IdFiller` 用生成器填充它。MyBatis 与 Spring Data JDBC 集成在插入前调用它，
也可以自行调用：`runtime.filler().fill(entity)`（在 Spring 中注入 `IdFiller`）。它属于 `cosid-core`（`me.ahoo.cosid.accessor`）。

```java
public class Order {
    @CosId                  // "default" 生成器
    private long id;
}

public class Bill {
    @CosId("bill")          // 指定名称的生成器
    private String id;      // 经生成器的编解码器转为文本
}

@CosId(value = "order", field = "orderId")   // 标注在类型上:指定字段名
public class LineItem extends BaseEntity { }
```

- 字段类型：`long` / `Long`、`int` / `Integer`（检查范围，超出抛 `IdOverflowException`），以及 `String`（经生成器的编解码器；
  `cosid80` 经其 `PartsCodec`，`periodic-segment` 经其 `GroupedCodec`，`uuidv7` 为规范的 UUID 文本；`uuidv7` 生成器只填充 `String` 字段）。
- **字段仅在未设置时才填充**：`≤ 0`、`null` 或空字符串。
- 有 getter 与 setter 时使用它们，否则直接访问字段。会搜索父类，并解析泛型字段类型。一个类最多有一个 ID。
- 每个类的访问器在首次使用时解析，并以 method handle 形式缓存。没有扫描或注册步骤（4.x 的访问器注册表与扫描器已移除）。
