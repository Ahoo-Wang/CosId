# Spring Data JDBC

`CosIdBeforeConvertCallback`（artifact `cosid-spring-data-jdbc`，包 `me.ahoo.cosid.spring.data.jdbc`）在聚合为保存而转换之前，先填充其
`@CosId` 字段，再用生成器（未配置时为 `default`）填充未设置的 `@Id`。

```java
public class Order {
    @Id
    private Long id;          // 未设置时由 cosid.jdbc.id-generator 填充
}
```

它默认关闭，与 4.x 相同：

| 属性 | 默认值 |
|---|---|
| `cosid.jdbc.enabled` | `false` |
| `cosid.jdbc.id-generator` | `default` |
